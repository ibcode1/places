package com.ib.placebook.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ib.placebook.R
import com.ib.placebook.ui.MapsActivity
import com.ib.placebook.viewmodel.MapsViewModel
import holder.ListBookmarkViewHolder

class BookmarkListAdapter(
    private var bookmarkData: List<MapsViewModel.BookmarkView>?,
    private val mapsActivity: MapsActivity,
    val clickListener: ListSelectionRecyclerViewClickListener
) : RecyclerView.Adapter<ListBookmarkViewHolder>() {

    fun setBookmarkData(bookmarks: List<MapsViewModel.BookmarkView>) {
        this.bookmarkData = bookmarks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListBookmarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.bookmark_item, parent, false)
        return ListBookmarkViewHolder(view, mapsActivity)
    }

    interface ListSelectionRecyclerViewClickListener {
        fun listItemClicked(bookmarkView: MapsViewModel.BookmarkView)
    }

    override fun onBindViewHolder(holder: ListBookmarkViewHolder, position: Int) {
        val bookmarkData = bookmarkData ?: return
        val bookmarkViewData = bookmarkData[position]

        holder.itemView.tag = bookmarkData

        holder.nameTextView.text = bookmarkViewData.name
        //holder.categoryImageView.setImageResource(R.drawable.ic_other)
        bookmarkViewData.categoryResourceId?.let {
            holder.categoryImageView.setImageResource(it)
        }

        holder.itemView.setOnClickListener {
            clickListener.listItemClicked(bookmarkViewData)
        }
    }

    override fun getItemCount(): Int {
        return bookmarkData?.size ?: 0
    }


}
