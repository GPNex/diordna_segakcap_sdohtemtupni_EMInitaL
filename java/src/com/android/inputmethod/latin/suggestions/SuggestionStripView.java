/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.keyboard.MoreKeysPanel;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.suggestions.MoreSuggestions.MoreSuggestionsListener;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.ImportantNoticeUtils;
import com.android.inputmethod.research.ResearchLogger;

import java.util.ArrayList;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        public void addWordToUserDictionary(String word);
        public void showImportantNoticeContents();
        public void pickSuggestionManually(int index, SuggestedWordInfo word);
    }

    static final boolean DBG = LatinImeLogger.sDBG;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.0f;

    private final ViewGroup mSuggestionsStrip;
    private final ViewGroup mAddToDictionaryStrip;
    private final View mImportantNoticeStrip;
    MainKeyboardView mMainKeyboardView;

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = CollectionUtils.newArrayList();
    private final ArrayList<TextView> mDebugInfoViews = CollectionUtils.newArrayList();
    private final ArrayList<View> mDividerViews = CollectionUtils.newArrayList();

    Listener mListener;
    private SuggestedWords mSuggestedWords = SuggestedWords.EMPTY;

    private final SuggestionStripLayoutHelper mLayoutHelper;
    private final StripVisibilityGroup mStripVisibilityGroup;

    private static class StripVisibilityGroup {
        private final View mSuggestionsStrip;
        private final View mAddToDictionaryStrip;
        private final View mImportantNoticeStrip;

        public StripVisibilityGroup(final View suggestionsStrip, final View addToDictionaryStrip,
                final View importantNoticeStrip) {
            mSuggestionsStrip = suggestionsStrip;
            mAddToDictionaryStrip = addToDictionaryStrip;
            mImportantNoticeStrip = importantNoticeStrip;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final boolean isRtlLanguage) {
            final int layoutDirection = isRtlLanguage ? ViewCompat.LAYOUT_DIRECTION_RTL
                    : ViewCompat.LAYOUT_DIRECTION_LTR;
            ViewCompat.setLayoutDirection(mSuggestionsStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mAddToDictionaryStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mImportantNoticeStrip, layoutDirection);
        }

        public void showSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(VISIBLE);
            mAddToDictionaryStrip.setVisibility(INVISIBLE);
            mImportantNoticeStrip.setVisibility(INVISIBLE);
        }

        public void showAddToDictionaryStrip() {
            mSuggestionsStrip.setVisibility(INVISIBLE);
            mAddToDictionaryStrip.setVisibility(VISIBLE);
            mImportantNoticeStrip.setVisibility(INVISIBLE);
        }

        public void showImportantNoticeStrip() {
            mSuggestionsStrip.setVisibility(INVISIBLE);
            mAddToDictionaryStrip.setVisibility(INVISIBLE);
            mImportantNoticeStrip.setVisibility(VISIBLE);
        }

        public boolean isShowingAddToDictionaryStrip() {
            return mAddToDictionaryStrip.getVisibility() == VISIBLE;
        }
    }

    /**
     * Construct a {@link SuggestionStripView} for showing suggestions to be picked by the user.
     * @param context
     * @param attrs
     */
    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionStripViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mSuggestionsStrip = (ViewGroup)findViewById(R.id.suggestions_strip);
        mAddToDictionaryStrip = (ViewGroup)findViewById(R.id.add_to_dictionary_strip);
        mImportantNoticeStrip = findViewById(R.id.important_notice_strip);
        mStripVisibilityGroup = new StripVisibilityGroup(mSuggestionsStrip, mAddToDictionaryStrip,
                mImportantNoticeStrip);

        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            divider.setOnClickListener(this);
            mDividerViews.add(divider);
            final TextView info = new TextView(context, null, R.attr.suggestionWordStyle);
            info.setTextColor(Color.WHITE);
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP);
            mDebugInfoViews.add(info);
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(
                context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = (MoreSuggestionsView)mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(
                context, mMoreSuggestionsSlidingListener);
    }

    /**
     * A connection back to the input method.
     * @param listener
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = (MainKeyboardView)inputView.findViewById(R.id.keyboard_view);
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        mStripVisibilityGroup.setLayoutDirection(isRtlLanguage);
        mSuggestedWords = suggestedWords;
        mLayoutHelper.layout(mSuggestedWords, mSuggestionsStrip, this);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.suggestionStripView_setSuggestions(mSuggestedWords);
        }
        mStripVisibilityGroup.showSuggestionsStrip();
    }

    public int setMoreSuggestionsHeight(final int remainingHeight) {
        return mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    public boolean isShowingAddToDictionaryHint() {
        return mStripVisibilityGroup.isShowingAddToDictionaryStrip();
    }

    public void showAddToDictionaryHint(final String word) {
        mLayoutHelper.layoutAddToDictionaryHint(word, mAddToDictionaryStrip, getWidth());
        // {@link TextView#setTag()} is used to hold the word to be added to dictionary. The word
        // will be extracted at {@link #onClick(View)}.
        mAddToDictionaryStrip.setTag(word);
        mAddToDictionaryStrip.setOnClickListener(this);
        mStripVisibilityGroup.showAddToDictionaryStrip();
    }

    public boolean dismissAddToDictionaryHint() {
        if (isShowingAddToDictionaryHint()) {
            clear();
            return true;
        }
        return false;
    }

    // This method checks if we should show the important notice (checks on permanent storage if
    // it has been shown once already or not, and if in the setup wizard). If applicable, it shows
    // the notice. In all cases, it returns true if it was shown, false otherwise.
    public boolean maybeShowImportantNoticeTitle() {
        if (!ImportantNoticeUtils.hasNewImportantNotice(getContext())
                || ImportantNoticeUtils.isInSystemSetupWizard(getContext())) {
            return false;
        }
        final int width = getWidth();
        if (width <= 0) return false;
        mLayoutHelper.layoutImportantNotice(mImportantNoticeStrip, width);
        mStripVisibilityGroup.showImportantNoticeStrip();
        mImportantNoticeStrip.setOnClickListener(this);
        return true;
    }

    public void clear() {
        mSuggestionsStrip.removeAllViews();
        removeAllDebugInfoViews();
        mStripVisibilityGroup.showSuggestionsStrip();
        dismissMoreSuggestionsPanel();
    }

    private void removeAllDebugInfoViews() {
        // The debug info views may be placed as children views of this {@link SuggestionStripView}.
        for (final View debugInfoView : mDebugInfoViews) {
            final ViewParent parent = debugInfoView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup)parent).removeView(debugInfoView);
            }
        }
    }

    private final MoreSuggestionsListener mMoreSuggestionsListener = new MoreSuggestionsListener() {
        @Override
        public void onSuggestionSelected(final int index, final SuggestedWordInfo wordInfo) {
            mListener.pickSuggestionManually(index, wordInfo);
            dismissMoreSuggestionsPanel();
        }

        @Override
        public void onCancelInput() {
            dismissMoreSuggestionsPanel();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
        @Override
        public void onDismissMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onDismissMoreKeysPanel(panel);
        }

        @Override
        public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onShowMoreKeysPanel(panel);
        }

        @Override
        public void onCancelMoreKeysPanel(final MoreKeysPanel panel) {
            dismissMoreSuggestionsPanel();
        }
    };

    public boolean isShowingMoreSuggestionPanel() {
        return mMoreSuggestionsView.isShowingInParent();
    }

    public void dismissMoreSuggestionsPanel() {
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }

    @Override
    public boolean onLongClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.NOT_A_CODE, this);
        return showMoreSuggestions();
    }

    boolean showMoreSuggestions() {
        final Keyboard parentKeyboard = KeyboardSwitcher.getInstance().getKeyboard();
        if (parentKeyboard == null) {
            return false;
        }
        final SuggestionStripLayoutHelper layoutHelper = mLayoutHelper;
        if (!layoutHelper.mMoreSuggestionsAvailable) {
            return false;
        }
        final int stripWidth = getWidth();
        final View container = mMoreSuggestionsContainer;
        final int maxWidth = stripWidth - container.getPaddingLeft() - container.getPaddingRight();
        final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
        builder.layout(mSuggestedWords, layoutHelper.mSuggestionsCountInStrip, maxWidth,
                (int)(maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
                layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        moreKeysPanel.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
                mMoreSuggestionsListener);
        mMoreSuggestionsMode = MORE_SUGGESTIONS_CHECKING_MODAL_OR_SLIDING;
        mOriginX = mLastX;
        mOriginY = mLastY;
        for (int i = 0; i < layoutHelper.mSuggestionsCountInStrip; i++) {
            mWordViews.get(i).setPressed(false);
        }
        return true;
    }

    // Working variables for onLongClick and dispatchTouchEvent.
    private int mMoreSuggestionsMode = MORE_SUGGESTIONS_IN_MODAL_MODE;
    private static final int MORE_SUGGESTIONS_IN_MODAL_MODE = 0;
    private static final int MORE_SUGGESTIONS_CHECKING_MODAL_OR_SLIDING = 1;
    private static final int MORE_SUGGESTIONS_IN_SLIDING_MODE = 2;
    private int mLastX;
    private int mLastY;
    private int mOriginX;
    private int mOriginY;
    private final int mMoreSuggestionsModalTolerance;
    private final GestureDetector mMoreSuggestionsSlidingDetector;
    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent me, float deltaX, float deltaY) {
            final float dy = me.getY() - down.getY();
            if (deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean dispatchTouchEvent(final MotionEvent me) {
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int)me.getX();
            mLastY = (int)me.getY();
            if (mMoreSuggestionsSlidingDetector.onTouchEvent(me)) {
                return true;
            }
            return super.dispatchTouchEvent(me);
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);

        if (mMoreSuggestionsMode == MORE_SUGGESTIONS_CHECKING_MODAL_OR_SLIDING) {
            if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                    || mOriginY - y >= mMoreSuggestionsModalTolerance) {
                // Decided to be in the sliding input mode only when the touch point has been moved
                // upward.
                mMoreSuggestionsMode = MORE_SUGGESTIONS_IN_SLIDING_MODE;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                // Decided to be in the modal input mode
                mMoreSuggestionsMode = MORE_SUGGESTIONS_IN_MODAL_MODE;
                mMoreSuggestionsView.adjustVerticalCorrectionForModalMode();
            }
            return true;
        }

        // MORE_SUGGESTIONS_IN_SLIDING_MODE
        me.setLocation(mMoreSuggestionsView.translateX(x), mMoreSuggestionsView.translateY(y));
        mMoreSuggestionsView.onTouchEvent(me);
        return true;
    }

    @Override
    public void onClick(final View view) {
        if (view == mImportantNoticeStrip) {
            mListener.showImportantNoticeContents();
            return;
        }
        final Object tag = view.getTag();
        // {@link String} tag is set at {@link #showAddToDictionaryHint(String,CharSequence)}.
        if (tag instanceof String) {
            final String wordToSave = (String)tag;
            mListener.addWordToUserDictionary(wordToSave);
            clear();
            return;
        }

        // {@link Integer} tag is set at
        // {@link SuggestionStripLayoutHelper#setupWordViewsTextAndColor(SuggestedWords,int)} and
        // {@link SuggestionStripLayoutHelper#layoutPunctuationSuggestions(SuggestedWords,ViewGroup}
        if (tag instanceof Integer) {
            final int index = (Integer) tag;
            if (index >= mSuggestedWords.size()) {
                return;
            }
            final SuggestedWordInfo wordInfo = mSuggestedWords.getInfo(index);
            mListener.pickSuggestionManually(index, wordInfo);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissMoreSuggestionsPanel();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overriden by showing suggestions later, if applicable.
        maybeShowImportantNoticeTitle();
    }
}
