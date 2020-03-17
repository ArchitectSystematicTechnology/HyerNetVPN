package se.leap.bitmaskclient.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import se.leap.bitmaskclient.R;


public class IconTextEntry extends LinearLayout {

    private TextView textView;
    private ImageView iconView;
    private TextView subtitleView;

    public IconTextEntry(Context context) {
        super(context);
        initLayout(context, null);
    }

    public IconTextEntry(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initLayout(context, attrs);
    }

    public IconTextEntry(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initLayout(context, attrs);
    }

    @TargetApi(21)
    public IconTextEntry(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initLayout(context, attrs);
    }

    void initLayout(Context context, AttributeSet attrs) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rootview = inflater.inflate(R.layout.v_icon_text_list_item, this, true);
        textView = rootview.findViewById(android.R.id.text1);
        subtitleView = rootview.findViewById(R.id.subtitle);
        iconView = rootview.findViewById(R.id.material_icon);

        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.IconTextEntry);

            String entryText = typedArray.getString(R.styleable.IconTextEntry_text);
            if (entryText != null) {
                textView.setText(entryText);
            }

            String subtitle = typedArray.getString(R.styleable.IconTextEntry_subtitle);
            if (subtitle != null) {
                subtitleView.setText(subtitle);
                subtitleView.setVisibility(VISIBLE);
            }

            Drawable drawable = typedArray.getDrawable(R.styleable.IconTextEntry_icon);
            if (drawable != null) {
                iconView.setImageDrawable(drawable);
            }

            typedArray.recycle();
        }


    }

    public void setText(@StringRes int id) {
        textView.setText(id);
    }

    public void setSubtitle(String text) {
        subtitleView.setText(text);
        subtitleView.setVisibility(VISIBLE);
    }

    public void hideSubtitle() {
        subtitleView.setVisibility(GONE);
    }

    public void setSubtitleColor(@ColorRes int color) {
        subtitleView.setTextColor(getContext().getResources().getColor(color));
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public void setIcon(@DrawableRes int id) {
        iconView.setImageResource(id);
    }

}
