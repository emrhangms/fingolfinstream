package com.fingolfin

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CizgiVeDiziPlugin : Plugin() {
    override fun load(context: Context) {
        // Çizgi ve Dizi sağlayıcısını Cloudstream'e kaydediyoruz
        registerMainAPI(CizgiVeDiziProvider())
    }
}
