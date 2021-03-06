package org.koitharu.kotatsu.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.koitharu.kotatsu.core.db.entity.FavouriteCategoryEntity

@Dao
abstract class FavouriteCategoriesDao {

	@Query("SELECT * FROM favourite_categories ORDER BY sort_key")
	abstract suspend fun findAll(): List<FavouriteCategoryEntity>

	@Insert(onConflict = OnConflictStrategy.ABORT)
	abstract suspend fun insert(category: FavouriteCategoryEntity): Long

	@Query("DELETE FROM favourite_categories WHERE category_id = :id")
	abstract suspend fun delete(id: Long)

	@Query("UPDATE favourite_categories SET title = :title WHERE category_id = :id")
	abstract suspend fun update(id: Long, title: String)

	@Query("UPDATE favourite_categories SET sort_key = :sortKey WHERE category_id = :id")
	abstract suspend fun update(id: Long, sortKey: Int)

	@Query("SELECT MAX(sort_key) FROM favourite_categories")
	protected abstract suspend fun getMaxSortKey(): Int?

	suspend fun getNextSortKey(): Int {
		return (getMaxSortKey() ?: 0) + 1
	}
}