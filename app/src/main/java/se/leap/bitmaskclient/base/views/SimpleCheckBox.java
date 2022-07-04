package se.leap.bitmaskclient.base.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.appcompat.widget.AppCompatImageView;

import java.lang.ref.WeakReference;

import se.leap.bitmaskclient.R;

public class SimpleCheckBox extends RelativeLayout {

    AppCompatImageView checkView;
    View checkBg;
    private WeakReference<OnCheckedChangeListener> checkedChangeListener = new WeakReference<OnCheckedChangeListener>(null);
    private boolean checked;
    private boolean enabled = true;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(SimpleCheckBox simpleCheckBox, boolean isChecked);
    }


    public SimpleCheckBox(Context context) {
        super(context);
        initLayout(context, null);
    }

    public SimpleCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        initLayout(context, attrs);
    }

    public SimpleCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initLayout(context, attrs);
    }

    @TargetApi(21)
    public SimpleCheckBox(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initLayout(context, attrs);
    }

    private void initLayout(Context context, AttributeSet attributeSet) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rootview = inflater.inflate(R.layout.v_simple_checkbox, this, true);
        this.checkView = rootview.findViewById(R.id.check_view);
        this.checkBg =  rootview.findViewById(R.id.check_bg);

        if (attributeSet != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.SimpleCheckBox);

            this.enabled = typedArray.getBoolean(R.styleable.SimpleCheckBox_enabled, true);
            this.checkView.setEnabled(enabled);
            checkBg.setEnabled(enabled);

            typedArray.recycle();
        }

    }

    public void setChecked(boolean checked) {
        if (!enabled){
            return;
        }

        if (this.checked != checked) {
            this.checkView.setVisibility(checked ? VISIBLE : INVISIBLE);
            this.checked = checked;
            OnCheckedChangeListener listener = checkedChangeListener.get();
            if (listener != null) {
                listener.onCheckedChanged(this, this.checked);
            }

        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        checkedChangeListener = new WeakReference<>(listener);
    }

    public void toggle() {
        setChecked(!this.checked);
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.checkView.setEnabled(enabled);
        this.checkBg.setEnabled(enabled);
    }

    public boolean isChecked() {
        return checked;
    }
}
