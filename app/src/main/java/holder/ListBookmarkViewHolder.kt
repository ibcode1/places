package holder

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ib.placebook.R
import com.ib.placebook.ui.MapsActivity

class ListBookmarkViewHolder(itemView: View, private val mapsActivity: MapsActivity) :
    RecyclerView.ViewHolder(itemView) {
    val nameTextView: TextView = itemView.findViewById(R.id.bookmarkNameTextView) as TextView
    val categoryImageView: ImageView = itemView.findViewById(R.id.bookmarkIcon) as ImageView

   /* init {
        itemView.setOnClickListener {
            val bookmarkView=itemView.tag as MapsViewModel.BookmarkView
            mapsActivity.moveToBookmark(bookmarkView)
        }
    }*/
}