package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPx
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

class TrackerPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_tracker_item
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val logoContainer = holder.findViewById(R.id.logo_container) as MaterialCardView
        val checkedIcon = holder.findViewById(R.id.checked_icon) as ImageView

        logoContainer.setCardBackgroundColor(iconColor)
        val padding = if (Color.alpha(iconColor) == 0) 0 else 4.dpToPx
        holder.findViewById(AR.id.icon).setPadding(padding)
        checkedIcon.isVisible = checked ?: !getPersistedString("").isNullOrEmpty()

        val longClickListener = onLongClick
        holder.itemView.setOnLongClickListener(
            longClickListener?.let { listener -> View.OnLongClickListener { listener() } },
        )
    }

    @ColorInt
    var iconColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            notifyChanged()
        }

    /**
     * Overrides the persisted-value check for the green check mark. Leave null to keep the
     * default behavior of showing it when the persisted string isn't empty.
     */
    var checked: Boolean? = null
        set(value) {
            field = value
            notifyChanged()
        }

    /** Optional long-press handler for the row. Return true when the press was handled. */
    var onLongClick: (() -> Boolean)? = null

    public override fun notifyChanged() {
        super.notifyChanged()
    }
}
