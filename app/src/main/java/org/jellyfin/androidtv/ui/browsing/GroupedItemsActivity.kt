package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import org.jellyfin.androidtv.R

class GroupedItemsActivity : FragmentActivity(R.layout.fragment_content_view) {
	private val groupingType get() = intent.extras?.getString(EXTRA_GROUPING_TYPE)?.let { GroupingType.valueOf(it) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		supportFragmentManager.commit {
			when (requireNotNull(groupingType)) {
				GroupingType.GENRE -> replace<ByGenreFragment>(R.id.content_view, args = intent.extras)
				GroupingType.LETTER -> replace<ByLetterFragment>(R.id.content_view, args = intent.extras)
			}
		}
	}

	enum class GroupingType {
		GENRE,
		LETTER,
	}

	companion object {
		const val EXTRA_GROUPING_TYPE = "type_grouping"
		const val EXTRA_FOLDER = "folder"
		const val EXTRA_INCLUDE_TYPE = "type_include"
	}
}
