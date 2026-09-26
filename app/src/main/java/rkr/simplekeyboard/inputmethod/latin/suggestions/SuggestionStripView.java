/*
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

package rkr.simplekeyboard.inputmethod.latin.suggestions;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;

/**
 * A row of word suggestions shown above the keyboard. The best suggestion is in the middle.
 */
public final class SuggestionStripView extends LinearLayout {
    public interface Listener {
        void onPickSuggestion(String word);
    }

    /** Slot order on screen for suggestions ranked 0, 1, 2: best in the middle. */
    private static final int[] SLOT_FOR_RANK = { 1, 0, 2 };

    private final TextView[] mSlots = new TextView[SuggestionController.MAX_SUGGESTIONS];
    private final View[] mDividers = new View[SuggestionController.MAX_SUGGESTIONS - 1];
    private final int mTextColor;
    private final int mPressedColor;
    private Listener mListener;

    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        // The keyboard view style gives the strip the same background as the keys area.
        super(context, attrs, defStyle);
        setOrientation(HORIZONTAL);

        final TypedArray keyAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard_Key, defStyle, R.style.KeyboardView);
        mTextColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyTextColor, Color.GRAY);
        keyAttr.recycle();
        final TypedArray themeAttr = context.obtainStyledAttributes(R.styleable.KeyboardTheme);
        mPressedColor = themeAttr.getColor(
                R.styleable.KeyboardTheme_keyPressedBackgroundColor, Color.TRANSPARENT);
        themeAttr.recycle();

        final Resources res = context.getResources();
        final int dividerWidth = Math.max(1, Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1, res.getDisplayMetrics())));
        final int dividerMargin = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10, res.getDisplayMetrics()));
        final int dividerColor = (mTextColor & 0x00FFFFFF) | 0x40000000;
        for (int i = 0; i < mSlots.length; i++) {
            if (i > 0) {
                final View divider = new View(context);
                divider.setBackgroundColor(dividerColor);
                final LayoutParams dividerParams =
                        new LayoutParams(dividerWidth, LayoutParams.MATCH_PARENT);
                dividerParams.topMargin = dividerMargin;
                dividerParams.bottomMargin = dividerMargin;
                addView(divider, dividerParams);
                mDividers[i - 1] = divider;
            }
            final TextView slot = new TextView(context);
            slot.setGravity(Gravity.CENTER);
            slot.setSingleLine(true);
            slot.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            slot.setTextColor(mTextColor);
            slot.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    res.getDimension(R.dimen.config_suggestion_text_size));
            if (i == SLOT_FOR_RANK[0]) {
                slot.setTypeface(Typeface.DEFAULT_BOLD);
            }
            slot.setBackground(createSlotBackground());
            slot.setOnClickListener(v -> {
                final CharSequence word = ((TextView) v).getText();
                if (mListener != null && word.length() > 0) {
                    mListener.onPickSuggestion(word.toString());
                }
            });
            addView(slot, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f));
            mSlots[i] = slot;
        }
        setSuggestions(null);
    }

    private StateListDrawable createSlotBackground() {
        final StateListDrawable background = new StateListDrawable();
        background.addState(new int[] { android.R.attr.state_pressed },
                new ColorDrawable(mPressedColor));
        background.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        return background;
    }

    public void setListener(final Listener listener) {
        mListener = listener;
    }

    /**
     * Applies the user's custom keyboard color, for themes that support it.
     */
    public void updateColors() {
        final Context context = getContext();
        final KeyboardTheme theme = Settings.getKeyboardTheme(context);
        if (theme.mCustomColorSupport) {
            final SharedPreferences prefs =
                    PreferenceManagerCompat.getDeviceSharedPreferences(context);
            setBackgroundColor(Settings.readKeyboardColor(prefs, context));
        }
    }

    /**
     * Shows the given suggestions, best first. Null or empty clears the strip.
     */
    public void setSuggestions(final List<String> suggestions) {
        final int count = suggestions == null ? 0 : suggestions.size();
        for (int rank = 0; rank < mSlots.length; rank++) {
            final TextView slot = mSlots[SLOT_FOR_RANK[rank]];
            final String word = rank < count ? suggestions.get(rank) : "";
            slot.setText(word);
            slot.setEnabled(!word.isEmpty());
            slot.setContentDescription(word.isEmpty() ? null
                    : getResources().getString(R.string.suggestion_description, word));
        }
        final int dividerVisibility = count > 1 ? VISIBLE : INVISIBLE;
        for (final View divider : mDividers) {
            divider.setVisibility(dividerVisibility);
        }
    }
}
