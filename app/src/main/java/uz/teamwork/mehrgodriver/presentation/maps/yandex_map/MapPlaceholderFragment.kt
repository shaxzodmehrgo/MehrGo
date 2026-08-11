package uz.teamwork.mehrgodriver.presentation.maps.yandex_map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 * Transparent, empty stand-in for the map's nav-graph destination.
 *
 * The real [MapFragment] is mounted ONCE, Activity-hosted, in the persistent `flPersistentMap`
 * container behind the NavHost (so its Yandex GL surface never leaves the window on in-app
 * navigation). But the nav graph still needs a destination with id `@id/mapFragment` so every
 * existing action, `popUpTo=@id/mapFragment`, `popBackStack(R.id.mapFragment)`, and
 * `getAction(R.id.action_mapFragment_*)` keeps resolving unchanged.
 *
 * This fragment fills that role: it renders nothing (fully transparent), so when it is the current
 * destination the persistent map underneath shows through. It holds no logic and no MapView.
 */
class MapPlaceholderFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
