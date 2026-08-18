package com.fingolfin

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class CizgiVeDiziPlugin : BasePlugin() {
    override fun load() {
        // Çizgi ve Dizi sağlayıcısını Cloudstream'e kaydediyoruz
        registerMainAPI(CizgiVeDiziProvider())
    }
}
