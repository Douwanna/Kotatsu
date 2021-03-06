package org.koitharu.kotatsu.ui.widget.shelf

import android.view.ViewGroup
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.ui.common.list.BaseRecyclerAdapter
import org.koitharu.kotatsu.ui.common.list.OnRecyclerItemClickListener

class CategorySelectAdapter(onItemClickListener: OnRecyclerItemClickListener<FavouriteCategory>? = null) :
	BaseRecyclerAdapter<FavouriteCategory, Boolean>(onItemClickListener) {

	var checkedItemId = 0L
		private set

	fun setCheckedId(id: Long) {
		val oldId = checkedItemId
		checkedItemId = id
		val oldPos = findItemPositionById(oldId)
		val newPos = findItemPositionById(id)
		if (newPos != -1) {
			notifyItemChanged(newPos)
		}
		if (oldPos != -1) {
			notifyItemChanged(oldPos)
		}
	}

	override fun getExtra(item: FavouriteCategory, position: Int) =
		checkedItemId == item.id

	override fun onCreateViewHolder(parent: ViewGroup) = CategorySelectHolder(
		parent
	)

	override fun onGetItemId(item: FavouriteCategory) = item.id
}