package com.fingolfin

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONArray

class CizgiVeDiziProvider : MainAPI() {
    override var mainUrl = "https://cizgivedizi.com"
    override var name = "Çizgi ve Dizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.TvSeries,
        TvType.Movie,
        TvType.Anime
    )

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override val mainPage = mainPageOf(
        "$mainUrl" to "Son Eklenenler"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(
            request.data,
            headers = mapOf("User-Agent" to USER_AGENT)
        ).document

        val home = ArrayList<HomePageList>()

        // 1. Son Eklenenler (.recent-card)
        val recentItems = document.select(".recent-card").mapNotNull { card ->
            val href = fixUrl(card.attr("href"))
            val title = card.selectFirst(".recent-name")?.text()?.trim() ?: return@mapNotNull null
            val poster = fixUrlNull(card.selectFirst("img")?.attr("src"))
            val isMovie = href.contains("/film/")

            if (isMovie) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.Cartoon) {
                    this.posterUrl = poster
                }
            }
        }
        if (recentItems.isNotEmpty()) {
            home.add(HomePageList("Son Eklenenler", recentItems))
        }

        // 2. Vitrindeki Diğer İçerikler
        val otherItems = document.select("#grid .item, .daily-card").mapNotNull { el ->
            val link = el.selectFirst("a") ?: el
            val href = fixUrl(link.attr("href"))
            if (href.isEmpty() || href == "#") return@mapNotNull null

            val title = el.selectFirst(".name, .title, h3, .poster-title")?.text()?.trim()
                ?: link.attr("title").ifEmpty { null }
                ?: return@mapNotNull null

            val poster = fixUrlNull(el.selectFirst("img")?.attr("src"))
            val isMovie = href.contains("/film/")

            if (isMovie) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.Cartoon) {
                    this.posterUrl = poster
                }
            }
        }
        if (otherItems.isNotEmpty()) {
            home.add(HomePageList("Öne Çıkanlar", otherItems))
        }

        return HomePageResponse(home)
    }

    private data class SearchResultItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("img") val img: String? = null,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("tip") val tip: String? = null
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "X-Requested-With" to "XMLHttpRequest"
        )
        val results = mutableListOf<SearchResponse>()

        // Sitenin AJAX arama uç noktaları (Dizi ve Film aramaları)
        val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")
        val diziSearchUrl = "$mainUrl/dizi/any?ajax=search&q=$encodedQ"
        val filmSearchUrl = "$mainUrl/film/any?ajax=search&q=$encodedQ"

        val diziList = try {
            app.get(diziSearchUrl, headers = headers).parsedSafe<List<SearchResultItem>>() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val filmList = try {
            app.get(filmSearchUrl, headers = headers).parsedSafe<List<SearchResultItem>>() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val allItems = (diziList + filmList).distinctBy { "${it.tip}_${it.id}" }

        for (item in allItems) {
            val title = item.name ?: continue
            val id = item.id ?: continue
            val slug = item.slug ?: id
            val isMovie = item.tip == "film"
            val typePrefix = if (isMovie) "film" else "dizi"
            val href = "$mainUrl/$typePrefix/$id/$slug"
            val poster = item.img

            if (isMovie) {
                results.add(newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                })
            } else {
                results.add(newTvSeriesSearchResponse(title, href, TvType.Cartoon) {
                    this.posterUrl = poster
                })
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        val isMovie = url.contains("/film/")

        val title = document.selectFirst("h1, .hero-title, .title")?.text()?.trim() ?: "İçerik"
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            ?: fixUrlNull(document.selectFirst(".poster-img, .hero-poster img, .pic img")?.attr("src"))

        val plot = document.selectFirst(".summary-content, .ep-summary-box, #heroDesc, .desc")?.text()?.trim()

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }

        // Dizi / Çizgi Dizi Bölümleri ve Sezonları
        val episodes = mutableListOf<Episode>()
        val seasonBlocks = document.select(".cvd-season-block")

        if (seasonBlocks.isNotEmpty()) {
            for (block in seasonBlocks) {
                val seasonHeaderText = block.selectFirst(".cvd-sh-text")?.text()?.trim() ?: ""
                val seasonNum = Regex("(\\d+)\\s*\\.?\\s*Sezon", RegexOption.IGNORE_CASE)
                    .find(seasonHeaderText)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                val entries = block.select(".cvd-episode-entry")
                for (entry in entries) {
                    val epHref = fixUrlNull(entry.attr("data-href")) ?: continue
                    val trName = entry.selectFirst(".cvd-col-title-tr")?.text()?.trim()
                    val engName = entry.selectFirst(".cvd-col-title-eng")?.text()?.trim()
                    val epName = trName?.ifEmpty { engName } ?: engName ?: "Bölüm"

                    val epNum = entry.selectFirst(".cvd-col-season")?.text()?.trim()?.toIntOrNull()
                        ?: Regex("/(\\d+)/").find(epHref)?.groupValues?.get(1)?.toIntOrNull()

                    episodes.add(
                        newEpisode(epHref) {
                            this.name = epName
                            this.season = seasonNum
                            this.episode = epNum
                        }
                    )
                }
            }
        }

        // Yedek bölüm ayıklama (Standart liste yapısı)
        if (episodes.isEmpty()) {
            val rows = document.select(".row[data-episode-id], a[href*='/dizi/'][data-episode-id]")
            for (row in rows) {
                val epHref = fixUrlNull(row.attr("href")) ?: continue
                val epName = row.selectFirst(".name")?.text()?.trim() ?: "Bölüm"
                val epNum = row.attr("data-episode-id").toIntOrNull()
                val epPoster = fixUrlNull(row.selectFirst("img")?.attr("src"))

                episodes.add(
                    newEpisode(epHref) {
                        this.name = epName
                        this.season = 1
                        this.episode = epNum
                        this.posterUrl = epPoster
                    }
                )
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = mapOf("User-Agent" to USER_AGENT)).document

        // 1. data-embeds veya __embeds_b64 içerisindeki Base64 listeyi yakala
        val b64Embeds = document.selectFirst("#videoDataContainer")?.attr("data-embeds")
            ?: Regex("""(?:__embeds_b64|data-embeds)\s*=\s*['"]([^'"]+)['"]""")
                .find(document.html())?.groupValues?.get(1)

        val embedUrls = mutableListOf<String>()

        if (!b64Embeds.isNullOrEmpty()) {
            try {
                val decodedJson = String(Base64.decode(b64Embeds, Base64.DEFAULT), Charsets.UTF_8)
                val jsonArray = JSONArray(decodedJson)
                for (i in 0 until jsonArray.length()) {
                    var link = jsonArray.getString(i).trim()
                    if (link.startsWith("//")) {
                        link = "https:$link"
                    }
                    if (link.isNotEmpty()) {
                        embedUrls.add(link)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Yedek iframe kontrolü
        if (embedUrls.isEmpty()) {
            document.select("iframe#playerFrame, .video-card iframe").forEach { iframe ->
                var src = iframe.attr("src").trim()
                if (src.isNotEmpty() && src != "about:blank") {
                    if (src.startsWith("//")) src = "https:$src"
                    embedUrls.add(src)
                }
            }
        }

        // 3. Bulunan tüm video kaynaklarını Cloudstream Extractor motoruna gönder
        for (embedUrl in embedUrls.distinct()) {
            loadExtractor(embedUrl, data, subtitleCallback, callback)
        }

        return embedUrls.isNotEmpty()
    }
}
