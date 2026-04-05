package org.pysh.janus.data

import androidx.annotation.StringRes
import org.pysh.janus.R

enum class SystemCard(
    val business: String,
    @param:StringRes val labelResId: Int,
) {
    MUSIC("music", R.string.system_card_music),
    INCALL("incall", R.string.system_card_incall),
    ALARM("alarm", R.string.system_card_alarm),
    COUNTDOWN("countdown", R.string.system_card_countdown),
    CAR_HAILING("carHailing", R.string.system_card_car_hailing),
    FOOD_DELIVERY("foodDelivery", R.string.system_card_food_delivery),
    XIAOMI_EV("xiaomiev", R.string.system_card_xiaomi_ev),
    PRIVACY("privacy", R.string.system_card_privacy),
    STOCK("stock", R.string.system_card_stock),
    MI_HOME_CAMERA("mihomeCamera", R.string.system_card_mi_home_camera),
    ;

    val customFileName: String get() = "${business}_custom.zip"
    val overridePrefsKey: String get() = "${business}_override_name"
    val customTemplateName: String get() = "${business}_custom"

    companion object {
        val nonMusic: List<SystemCard> = entries.filter { it != MUSIC }

        fun fromBusiness(biz: String): SystemCard? = entries.find { it.business == biz }
    }
}
