package org.koitharu.kotatsu.core.parser.site

import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.ParseException
import org.koitharu.kotatsu.core.model.*
import org.koitharu.kotatsu.core.parser.RemoteMangaRepository
import org.koitharu.kotatsu.domain.MangaLoaderContext
import org.koitharu.kotatsu.utils.ext.*

abstract class GroupleRepository(loaderContext: MangaLoaderContext) :
	RemoteMangaRepository(loaderContext) {

	protected abstract val defaultDomain: String

	override val sortOrders = setOf(
		SortOrder.UPDATED, SortOrder.POPULARITY,
		SortOrder.NEWEST, SortOrder.RATING
		//FIXME SortOrder.ALPHABETICAL
	)

	override suspend fun getList(
		offset: Int,
		query: String?,
		sortOrder: SortOrder?,
		tag: MangaTag?
	): List<Manga> {
		val domain = conf.getDomain(defaultDomain)
		val doc = when {
			!query.isNullOrEmpty() -> loaderContext.httpPost(
				"https://$domain/search",
				mapOf("q" to query, "offset" to offset.toString())
			)
			tag == null -> loaderContext.httpGet(
				"https://$domain/list?sortType=${getSortKey(
					sortOrder
				)}&offset=$offset"
			)
			else -> loaderContext.httpGet(
				"https://$domain/list/genre/${tag.key}?sortType=${getSortKey(
					sortOrder
				)}&offset=$offset"
			)
		}.parseHtml()
		val root = doc.body().getElementById("mangaBox")
			?.selectFirst("div.tiles.row") ?: throw ParseException("Cannot find root")
		return root.select("div.tile").mapNotNull { node ->
			val imgDiv = node.selectFirst("div.img") ?: return@mapNotNull null
			val descDiv = node.selectFirst("div.desc") ?: return@mapNotNull null
			if (descDiv.selectFirst("i.fa-user") != null) {
				return@mapNotNull null //skip author
			}
			val href = imgDiv.selectFirst("a").attr("href")?.withDomain(domain)
				?: return@mapNotNull null
			val title = descDiv.selectFirst("h3")?.selectFirst("a")?.text()
				?: return@mapNotNull null
			val tileInfo = descDiv.selectFirst("div.tile-info")
			Manga(
				id = href.longHashCode(),
				url = href,
				title = title,
				altTitle = descDiv.selectFirst("h4")?.text(),
				coverUrl = imgDiv.selectFirst("img.lazy")?.attr("data-original").orEmpty(),
				rating = safe {
					node.selectFirst("div.rating")
						?.attr("title")
						?.substringBefore(' ')
						?.toFloatOrNull()
						?.div(10f)
				} ?: Manga.NO_RATING,
				author = tileInfo?.selectFirst("a.person-link")?.text(),
				tags = safe {
					tileInfo?.select("a.element-link")
						?.map {
							MangaTag(
								title = it.text(),
								key = it.attr("href").substringAfterLast('/'),
								source = source
							)
						}?.toSet()
				}.orEmpty(),
				state = when {
					node.selectFirst("div.tags")
						?.selectFirst("span.mangaCompleted") != null -> MangaState.FINISHED
					else -> null
				},
				source = source
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val domain = conf.getDomain(defaultDomain)
		val doc = loaderContext.httpGet(manga.url).parseHtml()
		val root = doc.body().getElementById("mangaBox")?.selectFirst("div.leftContent")
			?: throw ParseException("Cannot find root")
		return manga.copy(
			description = root.selectFirst("div.manga-description")?.html(),
			largeCoverUrl = root.selectFirst("div.subject-cower")?.selectFirst("img")?.attr(
				"data-full"
			),
			tags = manga.tags + root.select("div.subject-meta").select("span.elem_genre ")
				.mapNotNull {
					val a = it.selectFirst("a.element-link") ?: return@mapNotNull null
					MangaTag(
						title = a.text(),
						key = a.attr("href").substringAfterLast('/'),
						source = source
					)
				},
			chapters = root.selectFirst("div.chapters-link")?.selectFirst("table")
				?.select("a")?.asReversed()?.mapIndexedNotNull { i, a ->
					val href =
						a.attr("href")?.withDomain(domain) ?: return@mapIndexedNotNull null
					MangaChapter(
						id = href.longHashCode(),
						name = a.ownText().removePrefix(manga.title).trim(),
						number = i + 1,
						url = href,
						source = source
					)
				}
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = loaderContext.httpGet(chapter.url + "?mtr=1").parseHtml()
		val scripts = doc.select("script")
		for (script in scripts) {
			val data = script.html()
			val pos = data.indexOf("rm_h.init")
			if (pos == -1) {
				continue
			}
			val json = data.substring(pos).substringAfter('[').substringBeforeLast(']')
			val matches = Regex("\\[.*?]").findAll(json).toList()
			val regex = Regex("['\"].*?['\"]")
			return matches.map { x ->
				val parts = regex.findAll(x.value).toList()
				val url = parts[0].value.removeSurrounding('"', '\'') +
						parts[2].value.removeSurrounding('"', '\'')
				MangaPage(
					id = url.longHashCode(),
					url = url,
					source = source
				)
			}
		}
		throw ParseException("Pages list not found at ${chapter.url}")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val domain = conf.getDomain(defaultDomain)
		val doc = loaderContext.httpGet("https://$domain/list/genres/sort_name").parseHtml()
		val root = doc.body().getElementById("mangaBox").selectFirst("div.leftContent")
			.selectFirst("table.table")
		return root.select("a.element-link").map { a ->
			MangaTag(
				title = a.text().capitalize(),
				key = a.attr("href").substringAfterLast('/'),
				source = source
			)
		}.toSet()
	}

	override fun onCreatePreferences() = setOf(R.string.key_parser_domain)

	private fun getSortKey(sortOrder: SortOrder?) =
		when (sortOrder ?: sortOrders.minBy { it.ordinal }) {
			SortOrder.ALPHABETICAL -> "name"
			SortOrder.POPULARITY -> "rate"
			SortOrder.UPDATED -> "updated"
			SortOrder.NEWEST -> "created"
			SortOrder.RATING -> "votes"
			null -> "updated"
		}
}