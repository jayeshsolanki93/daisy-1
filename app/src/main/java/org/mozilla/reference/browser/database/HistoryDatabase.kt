/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This class is derived from github.com/anthonycr/Lightning-Browser
 */
package org.mozilla.reference.browser.database

import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Bundle
import android.util.Log
import mozilla.components.concept.storage.HistoryAutocompleteResult
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.SearchResult
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.concept.storage.VisitType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.database.model.TopSite
import java.net.URI
import java.util.Locale

/**
 *Ported the class from Cliqz browser. This class implements the HistoryStorage interface
 * which makes it compatible with the Mozilla components.
 * The implemented methods are wired to the existing methods of the class.
 */
@Suppress("LargeClass", "TooManyFunctions")
class HistoryDatabase(context: Context)
    : SQLiteOpenHelper(context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION), HistoryStorage {

    override suspend fun deleteEverything() {
        clearHistory(false)
    }

    override suspend fun deleteVisit(url: String, timestamp: Long) {
        deleteHistoryPoint(url, timestamp)
    }

    override suspend fun deleteVisitsBetween(startTime: Long, endTime: Long) {
    }

    override suspend fun deleteVisitsFor(url: String) {
    }

    override suspend fun deleteVisitsSince(l: Long) {
    }

    override fun getAutocompleteSuggestion(s: String): HistoryAutocompleteResult? {
        return null
    }

    override suspend fun getDetailedVisits(start: Long, end: Long, excludeTypes: List<VisitType>): List<VisitInfo> {
        return listOf()
    }

    override fun getSuggestions(query: String, limit: Int): List<SearchResult> {
        val db = dbHandler.database ?: return listOf()
        val formattedSearch = String.format("%%%s%%", query)
        val selectQuery = res.getString(R.string.seach_history_query_v5)
        val cursor = db.rawQuery(selectQuery, arrayOf(formattedSearch, formattedSearch, limit.toString()))

        val searchSuggestions = mutableListOf<SearchResult>()
        val resultCount = 0
        if (cursor.moveToFirst()) {
            do {
                val url = cursor.getString(cursor.getColumnIndex(UrlsTable.URL))
                val title = cursor.getString(cursor.getColumnIndex(UrlsTable.TITLE))
                // The search query we use right now does not return any 'score' attribute column
                val searchSuggestion = SearchResult(url, url, 0, title)
                searchSuggestions.add(searchSuggestion)
            } while (cursor.moveToNext() && resultCount < limit)
        }
        cursor.close()
        return searchSuggestions
    }

    override suspend fun getVisited(): List<String> {
        return listOf()
    }

    override suspend fun getVisited(uris: List<String>): List<Boolean> {
        return listOf()
    }

    override suspend fun getVisitsPaginated(offset: Long, count: Long, excludeTypes: List<VisitType>): List<VisitInfo> {
        return getHistoryItems(offset, count)
    }

    override suspend fun prune() {
    }

    // looks like this method is used for updating the metadata so we add the title to our url visits here.
    override suspend fun recordObservation(uri: String, observation: PageObservation) {
        updateMetaData(uri, observation.title)
    }

    // this method records the actual history visits
    override suspend fun recordVisit(uri: String, visit: PageVisit) {
        // open to discussion how we should handle/record redirects
        if (visit.visitType == VisitType.LINK || visit.visitType == VisitType.TYPED) {
            visitHistoryItem(uri, "untitled")
        }
    }

    override fun cleanup() {
    }

    override suspend fun runMaintenance() {
    }

    // HistoryItems table name
    private object UrlsTable {
        const val TABLE_NAME = "urls"
        // Columns
        const val ID = "id"
        const val URL = "url"
        const val DOMAIN = "domain" // Added in v6
        const val TITLE = "title"
        const val VISITS = "visits"
        const val TIME = "time"
        const val FAVORITE = "favorite"
        const val FAV_TIME = "fav_time"
    }

    private object HistoryTable {
        const val TABLE_NAME = "history"
        // Columns
        const val ID = "id"
        const val URL_ID = "url_id"
        const val TIME = "time"
    }

    private object BlockedTopSitesTable {
        const val TABLE_NAME = "blocked_topsites"
        // Columns
        const val DOMAIN = "domain"
    }

    private object QueriesTable {
        const val TABLE_NAME = "queries"
        // Columns
        const val ID = "id"
        const val QUERY = "query"
        const val TIME = "time"
    }

    object HistoryKeys {
        // Fields
        const val HISTORY_ID = "id"
        const val URL = "url"
        const val TITLE = "title"
        const val TIME = "timestamp"
    }

    private val res: Resources
    private val dbHandler: DatabaseHandler
    // Creating Tables
    override fun onCreate(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            createV9DB(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createV4DB(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.create_urls_table_v4))
        db.execSQL(res.getString(R.string.create_history_table_v4))
        db.execSQL(res.getString(R.string.create_urls_index_v4))
        db.execSQL(res.getString(R.string.create_visits_index_v4))
    }

    private fun createV9DB(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.create_urls_table_v6))
        db.execSQL(res.getString(R.string.create_history_table_v5))
        db.execSQL(res.getString(R.string.create_urls_index_v5))
        db.execSQL(res.getString(R.string.create_visits_index_v5))
        db.execSQL(res.getString(R.string.create_blocked_topsites_table_v6))
        db.execSQL(res.getString(R.string.create_queries_table_v7))
        db.execSQL(res.getString(R.string.create_history_time_index_v8))
        db.execSQL(res.getString(R.string.create_url_index_v9))
    }

    private fun upgradeV2toV3(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.alter_history_table_v2_to_v3))
        db.execSQL(res.getString(R.string.create_visits_index_v3))
        db.execSQL(res.getString(R.string.rename_history_table_to_tempHistory_v3_to_v4))
        db.execSQL(res.getString(R.string.drop_urlIndex_v3_to_v4))
        db.execSQL(res.getString(R.string.drop_countIndex_v3_to_v4))
        createV4DB(db)
        db.execSQL(res.getString(R.string.move_to_new_history_v3_to_v4))
        db.execSQL(res.getString(R.string.move_to_urls_v3_to_v4))
        db.execSQL(res.getString(R.string.drop_tempHistory_v3_to_v4))
        db.execSQL(res.getString(R.string.add_column_fav_time_v5))
        db.execSQL(res.getString(R.string.move_favorites_to_urls_v5))
        // Add the domain column
        db.execSQL(res.getString(R.string.add_column_domain_to_urls_v6))
        // Create the blocked topsites table
        db.execSQL(res.getString(R.string.create_blocked_topsites_table_v6))
        // create queries table
        db.execSQL(res.getString(R.string.create_queries_table_v7))
    }

    private fun upgradeV3toV4(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.rename_history_table_to_tempHistory_v3_to_v4))
        db.execSQL(res.getString(R.string.drop_urlIndex_v3_to_v4))
        db.execSQL(res.getString(R.string.drop_countIndex_v3_to_v4))
        createV4DB(db)
        db.execSQL(res.getString(R.string.move_to_new_history_v3_to_v4))
        db.execSQL(res.getString(R.string.move_to_urls_v3_to_v4))
        db.execSQL(res.getString(R.string.drop_tempHistory_v3_to_v4))
        db.execSQL(res.getString(R.string.add_column_fav_time_v5))
        db.execSQL(res.getString(R.string.move_favorites_to_urls_v5))
        db.execSQL(res.getString(R.string.add_column_domain_to_urls_v6))
        db.execSQL(res.getString(R.string.create_blocked_topsites_table_v6))
        db.execSQL(res.getString(R.string.create_queries_table_v7))
    }

    private fun upgradeV4toV5(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.add_column_fav_time_v5))
        db.execSQL(res.getString(R.string.move_favorites_to_urls_v5))
        db.execSQL(res.getString(R.string.add_column_domain_to_urls_v6))
        db.execSQL(res.getString(R.string.create_blocked_topsites_table_v6))
        db.execSQL(res.getString(R.string.create_queries_table_v7))
    }

    private fun upgradeV5toV6(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.add_column_domain_to_urls_v6))
        db.execSQL(res.getString(R.string.create_blocked_topsites_table_v6))
        db.execSQL(res.getString(R.string.create_queries_table_v7))
    }

    private fun upgradeV6toV7(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.create_queries_table_v7))
    }

    private fun upgradeV7toV8(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.create_history_time_index_v8))
    }

    private fun upgradeV8toV9(db: SQLiteDatabase) {
        db.execSQL(res.getString(R.string.create_url_index_v9))
    }

    // Upgrading database
    @Suppress("ComplexMethod")
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.beginTransaction()
        try {
            if (oldVersion < V3) upgradeV2toV3(db)
            if (oldVersion < V4) upgradeV3toV4(db)
            if (oldVersion < V5) upgradeV4toV5(db)
            if (oldVersion < V6) upgradeV5toV6(db)
            if (oldVersion < V7) upgradeV6toV7(db)
            if (oldVersion < V8) upgradeV7toV8(db)
            if (oldVersion < V9) upgradeV8toV9(db)
            else {
                db.execSQL("DROP TABLE IF EXISTS " + UrlsTable.TABLE_NAME)
                db.execSQL("DROP TABLE IF EXISTS " + HistoryTable.TABLE_NAME)
                db.execSQL("DROP TABLE IF EXISTS " + BlockedTopSitesTable.TABLE_NAME)
                db.execSQL("DROP TABLE IF EXISTS " + QueriesTable.TABLE_NAME)
                // Create tables again
                createV9DB(db)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    override fun close() {
        dbHandler.close()
        super.close()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun updateMetaData(url: String, title: String?) {
        val db = dbHandler.database ?: return
        val q = db.query(false, UrlsTable.TABLE_NAME, arrayOf(UrlsTable.ID, UrlsTable.VISITS, UrlsTable.TIME),
            UrlsTable.URL + " = ?", arrayOf(url), null, null, null, "1")
        val urlsValues = ContentValues()
        val domain = extractDomainFrom(url)
        urlsValues.put(UrlsTable.URL, url)
        urlsValues.put(UrlsTable.DOMAIN, domain)
        urlsValues.put(UrlsTable.TITLE, title)
        db.beginTransaction()
        try {
            val urlId: Long
            if (q.count > 0) {
                q.moveToFirst()
                val idIndex = q.getColumnIndex(UrlsTable.ID)
                val visitsIndex = q.getColumnIndex(UrlsTable.VISITS)
                val timeIndex = q.getColumnIndex(UrlsTable.TIME)
                urlId = q.getLong(idIndex)
                val visits = q.getLong(visitsIndex)
                urlsValues.put(UrlsTable.VISITS, visits)
                urlsValues.put(UrlsTable.TIME, q.getLong(timeIndex))
                db.update(UrlsTable.TABLE_NAME, urlsValues, UrlsTable.ID + " = ?",
                    arrayOf(urlId.toString()))
            }
            q.close()
            db.setTransactionSuccessful()
        } catch (e: Exception) { // We do not want to crash if we can't update history
            Log.e("HistoryDatabase", "Error updating meta data", e)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Update an history and urls
     *
     * @param url the url to update
     * @param title the title of the page to which the url is pointing
     */
    @Suppress("TooGenericExceptionCaught")
    @Synchronized
    fun visitHistoryItem(url: String, title: String?): Long {
        val db = dbHandler.database ?: return -1
        val q = db.query(false, UrlsTable.TABLE_NAME, arrayOf(UrlsTable.ID, UrlsTable.VISITS),
            UrlsTable.URL + " = ?", arrayOf(url), null, null, null, "1")
        val time = System.currentTimeMillis()
        val urlsValues = ContentValues()
        val domain = extractDomainFrom(url)
        urlsValues.put(UrlsTable.URL, url)
        urlsValues.put(UrlsTable.DOMAIN, domain)
        urlsValues.put(UrlsTable.TITLE, title)
        urlsValues.put(UrlsTable.VISITS, 1L)
        urlsValues.put(UrlsTable.TIME, time)
        db.beginTransaction()
        val historyID: Long
        try {
            val urlId: Long
            if (q.count > 0) {
                q.moveToFirst()
                val idIndex = q.getColumnIndex(UrlsTable.ID)
                val visitsIndex = q.getColumnIndex(UrlsTable.VISITS)
                urlId = q.getLong(idIndex)
                val visits = q.getLong(visitsIndex)
                urlsValues.put(UrlsTable.VISITS, visits + 1L)
                db.update(UrlsTable.TABLE_NAME, urlsValues, UrlsTable.ID + " = ?",
                    arrayOf(java.lang.Long.toString(urlId)))
            } else {
                urlId = db.insert(UrlsTable.TABLE_NAME, null, urlsValues)
            }
            q.close()
            val historyValues = ContentValues()
            historyValues.put(HistoryTable.URL_ID, urlId)
            historyValues.put(HistoryTable.TIME, time)
            historyID = db.insert(HistoryTable.TABLE_NAME, null, historyValues)
            db.setTransactionSuccessful()
            return historyID
        } catch (e: Exception) { // We do not want to crash if we can't update history
            Log.e("HistoryDatabase", "Error updating history", e)
        } finally {
            db.endTransaction()
        }
        return -1
    }

    /**
     * Simply delete all the entries in the blocked_topsites table
     */
    fun restoreTopSites() {
        val db = dbHandler.database ?: return
        db.beginTransaction()
        try {
            db.delete(BlockedTopSitesTable.TABLE_NAME, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Query the history db to fetch the top most visited websites.
     *
     * @param limit the number of items to return
     * @return a list of [TopSite]. The time stamp of these elements is always -1.
     */
    @Suppress("NestedBlockDepth")
    @Synchronized
    fun getTopSites(limit: Int): List<TopSite> {
        var limit = limit
        val db = dbHandler.database ?: return listOf()
        if (limit < 1) {
            limit = 1
        } else if (limit > MAX_TOP_SITE_LIMIT) {
            limit = MAX_TOP_SITE_LIMIT
        }
        val topSites = ArrayList<TopSite>(limit)
        val cursor = db.rawQuery(res.getString(R.string.get_top_sites_v6), null)
        var counter = 0
        if (cursor.moveToFirst()) {
            val urlIndex = cursor.getColumnIndex(UrlsTable.URL)
            val titleIndex = cursor.getColumnIndex(UrlsTable.TITLE)
            val domainIndex = cursor.getColumnIndex(UrlsTable.DOMAIN)
            val idIndex = cursor.getColumnIndex(UrlsTable.ID)
            do {
                val domain = cursor.getString(domainIndex)
                val id = cursor.getLong(idIndex)
                val url = cursor.getString(urlIndex)
                if (domain == null) {
                    val domainToCheck = extractDomainFrom(url)
                    if (domainToCheck != null) {
                        patchDomainForUrlWithId(db, id, domainToCheck)
                        if (blockedDomain(db, domainToCheck)) {
                            continue
                        }
                    }
                }
                topSites.add(TopSite(id, url, domain
                        ?: "", cursor.getString(titleIndex)))
                counter++
            } while (cursor.moveToNext() && counter < limit)
        }
        cursor.close()
        return topSites
    }

    @Deprecated("")
    @Synchronized
    fun findItemsContaining(search: String?, limit: Int): JSONArray {
        var limit = limit
        val itemList = JSONArray()
        if (search == null) {
            return itemList
        }
        val mDatabase = dbHandler.database ?: return itemList
        if (limit <= 0) {
            limit = MIN_SEARCH_LIMIT
        }
        val formattedSearch = String.format("%%%s%%", search)
        val selectQuery = res.getString(R.string.seach_history_query_v5)
        val cursor = mDatabase.rawQuery(selectQuery, arrayOf(
            formattedSearch,
            formattedSearch,
            Integer.toString(limit)
        ))
        var n = 0
        if (cursor.moveToFirst()) { // final int idIndex = cursor.getColumnIndex(UrlsTable.ID);
            val urlIndex = cursor.getColumnIndex(UrlsTable.URL)
            val titleIndex = cursor.getColumnIndex(UrlsTable.TITLE)
            do {
                try {
                    val item = JSONObject()
                    item.put(HistoryKeys.URL, cursor.getString(urlIndex))
                    item.put(HistoryKeys.TITLE, cursor.getString(titleIndex))
                    itemList.put(item)
                    n++
                } catch (e: JSONException) { // Ignore this org.json weirdness
                }
            } while (cursor.moveToNext() && n < limit)
        }
        cursor.close()
        return itemList
    }

    @Synchronized
    fun searchHistory(search: String?, limit: Int): Array<Bundle?> {
        var limit = limit
        if (search == null || dbHandler.database == null) {
            return arrayOfNulls(0)
        }
        val mDatabase = dbHandler.database
        if (limit <= 0) {
            limit = MIN_SEARCH_LIMIT
        }
        val lcSearch = search.toLowerCase()
        val formattedSearch = String.format("%%%s%%", lcSearch)
        val selectQuery = res.getString(R.string.seach_history_query_v8)
        val cursor = mDatabase!!.rawQuery(selectQuery, arrayOf(
            lcSearch,
            lcSearch,
            lcSearch,
            formattedSearch,
            formattedSearch,
            formattedSearch,
            Integer.toString(limit)
        ))
        val size = cursor.count
        val result = arrayOfNulls<Bundle>(size)
        val urlIndex: Int
        val titleIndex: Int
        if (cursor.moveToFirst()) {
            urlIndex = cursor.getColumnIndex(UrlsTable.URL)
            titleIndex = cursor.getColumnIndex(UrlsTable.TITLE)
        } else {
            urlIndex = -1
            titleIndex = -1
        }
        for (index in 0 until size) {
            val historyRecord = Bundle()
            historyRecord.putString(HistoryKeys.URL, cursor.getString(urlIndex))
            historyRecord.putString(HistoryKeys.TITLE, cursor.getString(titleIndex))
            result[index] = historyRecord
            cursor.moveToNext()
        }
        cursor.close()
        return result
    }

    @get:Synchronized
    val historyItemsCount: Int
        get() {
            val db = dbHandler.database ?: return 0
            val countQuery = "SELECT COUNT(*) FROM " + HistoryTable.TABLE_NAME
            val cursor = db.rawQuery(countQuery, null)
            val result = if (cursor.moveToNext()) cursor.getLong(0).toInt() else 0
            cursor.close()
            return result
        }

    @get:Synchronized
    val firstHistoryItemTimestamp: Long
        get() {
            val db = dbHandler.database ?: return -1
            val c = db.query(HistoryTable.TABLE_NAME, arrayOf(HistoryTable.TIME),
                null, null, null, null, String.format("%s ASC", HistoryTable.TIME),
                "1")
            val timestamp: Long
            timestamp = if (c.moveToFirst()) {
                val timestampIndex = c.getColumnIndex(HistoryTable.TIME)
                c.getLong(timestampIndex)
            } else {
                -1
            }
            c.close()
            return timestamp
        }

    @Synchronized
    fun getHistoryItemsForRecyclerView(offset: Int, limit: Int): Cursor? {
        val db = dbHandler.database ?: return null
        // TODO add limit and offset correctly; removed it due to a bug
        return db.rawQuery(res.getString(R.string.get_history_query_recyclerview_v7), null)
    }

    @Synchronized
    fun getHistoryItems(offset: Long, limit: Long): List<VisitInfo> {
        val results: MutableList<VisitInfo> = ArrayList()
        val db = dbHandler.database ?: return results
        val cursor = db.rawQuery(res.getString(R.string.get_history_query_v5), arrayOf(
            java.lang.Long.toString(limit),
            java.lang.Long.toString(offset)
        ))
        if (cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(HistoryTable.ID)
            val urlIndex = cursor.getColumnIndex(UrlsTable.URL)
            val titleIndex = cursor.getColumnIndex(UrlsTable.TITLE)
            val timeIndex = cursor.getColumnIndex(HistoryTable.TIME)
            do {
                val item = VisitInfo(
                    cursor.getString(urlIndex),
                    cursor.getString(titleIndex),
                    cursor.getLong(timeIndex), VisitType.LINK)
                results.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return results
    }

    // Ignore this org.json weirdness
    @get:Synchronized
    val favorites: JSONArray
        get() {
            val results = JSONArray()
            val db = dbHandler.database ?: return results
            val cursor = db.rawQuery(res.getString(R.string.get_favorite_query_v5), null)
            if (cursor.moveToFirst()) {
                val urlIndex = cursor.getColumnIndex(UrlsTable.URL)
                val titleIndex = cursor.getColumnIndex(UrlsTable.TITLE)
                val favTimeIndex = cursor.getColumnIndex(UrlsTable.FAV_TIME)
                do {
                    try {
                        val item = JSONObject()
                        item.put(HistoryKeys.URL, cursor.getString(urlIndex))
                        item.put(HistoryKeys.TITLE, cursor.getString(titleIndex))
                        item.put(HistoryKeys.TIME, cursor.getLong(favTimeIndex))
                        results.put(item)
                    } catch (e: JSONException) { // Ignore this org.json weirdness
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
            return results
        }

    @Synchronized
    fun isFavorite(url: String): Boolean {
        val db = dbHandler.database ?: return false
        val cursor = db.query(UrlsTable.TABLE_NAME, arrayOf(UrlsTable.ID),
            String.format(Locale.US, "%s=? AND %s=1", UrlsTable.URL, UrlsTable.FAVORITE), arrayOf(url),
            null, null, null)
        val result = cursor.count > 0
        cursor.close()
        return result
    }

    @Synchronized
    fun setFavorites(url: String, title: String?, favTime: Long, isFavorite: Boolean) {
        val db = dbHandler.database ?: return
        val values = ContentValues()
        values.put(UrlsTable.FAVORITE, isFavorite)
        values.put(UrlsTable.FAV_TIME, favTime)
        val cursor = db.rawQuery(res.getString(R.string.search_url_v5), arrayOf(url))
        db.beginTransaction()
        try {
            if (cursor.count > 0) {
                db.update(UrlsTable.TABLE_NAME, values, "url = ?", arrayOf(url))
            } else {
                values.put(UrlsTable.URL, url)
                values.put(UrlsTable.TITLE, title)
                values.put(UrlsTable.VISITS, 0)
                db.insert(UrlsTable.TABLE_NAME, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        cursor.close()
    }

    /**
     * Delete an history point. If the history point is the last one for a given url and the url is
     * not favorite, the method will delete the url from the urls table also
     *
     * @param id the id of the history point
     */
    @Synchronized
    fun deleteHistoryPoint(url: String, timestamp: Long) {
        val db = dbHandler.database ?: return
        val idCursor = db.rawQuery(res.getString(R.string.get_history_id_from_url_and_time),
            arrayOf(url, timestamp.toString()))
        var id = -1L
        if (idCursor.moveToFirst()) {
            id = idCursor.getLong(idCursor.getColumnIndex(HistoryTable.ID))
        }
        idCursor.close()
        val cursor = db.rawQuery(res.getString(R.string.get_url_from_history_id_v5),
            arrayOf(java.lang.Long.toString(id)))
        if (cursor.moveToFirst()) {
            val uid = cursor.getLong(cursor.getColumnIndex(UrlsTable.ID))
            val visits = cursor.getLong(cursor.getColumnIndex(UrlsTable.VISITS)) - 1
            val favorite = cursor.getInt(cursor.getColumnIndex(UrlsTable.FAVORITE)) > 0
            db.beginTransaction()
            try {
                db.delete(HistoryTable.TABLE_NAME, "id=?", arrayOf(java.lang.Long.toString(id)))
                if (visits <= 0 && !favorite) {
                    db.delete(UrlsTable.TABLE_NAME, "id=?", arrayOf(java.lang.Long.toString(uid)))
                } else {
                    val value = ContentValues()
                    value.put(UrlsTable.VISITS, if (visits < 0) 0 else visits)
                    db.update(UrlsTable.TABLE_NAME, value, "id=?", arrayOf(java.lang.Long.toString(uid)))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        cursor.close()
    }

    /**
     * Clear the history which is not favored
     *
     * @param deleteFavorites if true unfavorite the favored items
     */
    @Synchronized
    fun clearHistory(deleteFavorites: Boolean) {
        val db = dbHandler.database ?: return
        db.beginTransaction()
        try {
            if (deleteFavorites) { // mark all entries in urls table as favorite = false
                val contentValues = ContentValues()
                contentValues.put(UrlsTable.FAVORITE, false)
                db.update(UrlsTable.TABLE_NAME, contentValues, null, null)
                // delete all entries with visits = 0;
                db.delete(UrlsTable.TABLE_NAME, "visits=0", null)
            } else { // empty history table
                db.delete(HistoryTable.TABLE_NAME, null, null)
                // delete rows where favorite != 1
                db.delete(UrlsTable.TABLE_NAME, "favorite<1", null)
                // update "visits" of remaining rows to 0
                val contentValues = ContentValues()
                contentValues.put(UrlsTable.VISITS, 0)
                db.update(UrlsTable.TABLE_NAME, contentValues, null, null)
            }
            db.delete(QueriesTable.TABLE_NAME, null, null)
            // way to flush ghost entries on older sqlite version
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                db.execSQL(res.getString(R.string.create_temp_urls_table_v6))
                db.execSQL("INSERT into urls_temp SELECT * from urls")
                db.execSQL("drop table urls")
                db.execSQL("drop table queries")
                db.execSQL(res.getString(R.string.create_urls_table_v6))
                db.execSQL(res.getString(R.string.create_queries_table_v7))
                db.execSQL(res.getString(R.string.create_urls_index_v5))
                db.execSQL(res.getString(R.string.create_visits_index_v5))
                db.execSQL("INSERT into urls SELECT * from urls_temp")
                db.execSQL("drop table urls_temp")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Update the title of the given history entry
     *
     * @param historyId the history entry id
     * @param title the new title
     */
    fun updateTitleFor(historyId: Long, title: String) {
        val db = dbHandler.database ?: return
        // First trace back the url id from the history id
        val cursor = db.rawQuery(res.getString(R.string.get_url_from_history_id_v5),
            arrayOf(java.lang.Long.toString(historyId)))
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndex(UrlsTable.ID))
            val contentValues = ContentValues()
            contentValues.put(UrlsTable.TITLE, title)
            db.beginTransaction()
            try {
                val where = String.format("%s = ?", UrlsTable.ID)
                db.update(UrlsTable.TABLE_NAME, contentValues, where, arrayOf(java.lang.Long.toString(id)))
                db.setTransactionSuccessful()
            } catch (ignore: Exception) {
            } finally {
                db.endTransaction()
            }
        }
        cursor.close()
    }

    /**
     * Add the domains to the blocked_topsites table
     *
     * @param domains one or more entries to add to the table
     */
    fun blockDomainsForTopsites(vararg domains: String) {
        if (domains.isEmpty()) {
            return
        }
        val db = dbHandler.database ?: return
        db.beginTransaction()
        try {
            for (domain in domains) {
                val values = ContentValues()
                values.put(BlockedTopSitesTable.DOMAIN, domain)
                db.insert(BlockedTopSitesTable.TABLE_NAME, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Removes the given domain from the list of BlockedTopSites i.e. the domain can now appear
     * again in the 'top sites' grid.
     *
     * @param domain the entry to remove from the table.
     */
    fun removeDomainFromBlockedTopSites(domain: String) {
        val db = dbHandler.database ?: return
        db.beginTransaction()
        try {
            val whereClause = BlockedTopSitesTable.DOMAIN + "=?"
            db.delete(BlockedTopSitesTable.TABLE_NAME, whereClause, arrayOf(domain))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun removeBlockedTopSites() {
        val db = dbHandler.database ?: return
        db.beginTransaction()
        try {
            if (historyItemsCount > 0) {
                db.delete(BlockedTopSitesTable.TABLE_NAME, null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun addQuery(query: String?) {
        val db = dbHandler.database ?: return
        val contentValues = ContentValues()
        contentValues.put(QueriesTable.QUERY, query)
        contentValues.put(QueriesTable.TIME, System.currentTimeMillis())
        db.beginTransaction()
        try {
            db.insert(QueriesTable.TABLE_NAME, null, contentValues)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteQuery(id: Long) {
        val db = dbHandler.database ?: return
        db.beginTransaction()
        try {
            db.delete(QueriesTable.TABLE_NAME, "id=?", arrayOf(java.lang.Long.toString(id)))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        // All Static variables
// Database Version
        private const val V3 = 3
        private const val V4 = 4
        private const val V5 = 5
        private const val V6 = 6
        private const val V7 = 7
        private const val V8 = 8
        private const val V9 = 9
        private const val MAX_TOP_SITE_LIMIT = 100
        private const val MIN_SEARCH_LIMIT = 5
        private const val DATABASE_VERSION = 9
        private const val DOMAIN_START_INDEX = 4
        // Database Name
        private const val DATABASE_NAME = "historyManager"

        private fun extractDomainFrom(url: String): String? {
            try {
                val uri = URI.create(url)
                val host = uri.host
                val domain: String?
                domain = if (host == null) {
                    null
                } else if (host.startsWith("www.")) {
                    host.substring(DOMAIN_START_INDEX)
                } else {
                    host
                }
                return domain
            } catch (e: IllegalArgumentException) {
                Log.e("HistoryDatabase", "Illegal url: $url", e)
            }
            return null
        }

        private fun blockedDomain(db: SQLiteDatabase, domain: String): Boolean {
            val cursor = db.query(BlockedTopSitesTable.TABLE_NAME, null, "domain = ?",
                arrayOf(domain), null, null, null)
            val result = cursor.moveToFirst()
            cursor.close()
            return result
        }

        private fun patchDomainForUrlWithId(db: SQLiteDatabase, id: Long, domain: String) {
            val domainValues = ContentValues()
            domainValues.put(UrlsTable.DOMAIN, domain)
            db.update(UrlsTable.TABLE_NAME, domainValues, UrlsTable.ID + " = ?",
                arrayOf(java.lang.Long.toString(id)))
        }
    }

    init {
        res = context.resources
        dbHandler = DatabaseHandler(this)
    }
}