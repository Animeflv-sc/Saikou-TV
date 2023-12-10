package ani.saikou.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import ani.saikou.BottomSheetDialogFragment
import ani.saikou.databinding.BottomSheetDevelopersBinding

class DevelopersDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDevelopersBinding? = null
    private val binding get() = _binding!!

    private val developers = arrayOf(
        Developer("vorobyovgabriel","https://avatars.githubusercontent.com/u/99561687?s=120&v=4","Owner","https://github.com/vorobyovgabriel"),
        Developer("brahmkshtriya","https://avatars.githubusercontent.com/u/69040506?s=120&v=4","Maintainer","https://github.com/brahmkshatriya"),
        Developer("jeelpatel231","https://avatars.githubusercontent.com/u/33726155?s=120&v=4","Contributor","https://github.com/jeelpatel231"),
        Developer("blatzar","https://avatars.githubusercontent.com/u/46196380?s=120&v=4","Contributor","https://github.com/Blatzar"),
        Developer("bilibox","https://avatars.githubusercontent.com/u/1800580?s=120&v=4","Contributor","https://github.com/Bilibox"),
        Developer("sutslec","https://avatars.githubusercontent.com/u/27722281?s=120&v=4","Contributor","https://github.com/Sutslec"),
        Developer("4jx","https://avatars.githubusercontent.com/u/79868816?s=120&v=4","Contributor","https://github.com/4JX"),
        Developer("xtrm-en","https://avatars.githubusercontent.com/u/26600206?s=120&v=4","Contributor","https://github.com/xtrm-en"),
        Developer("scrazzz","https://avatars.githubusercontent.com/u/70033559?s=120&v=4","Contributor","https://github.com/scrazzz"),
        Developer("defcoding","https://avatars.githubusercontent.com/u/39608887?s=120&v=4","Contributor","https://github.com/defcoding"),
        Developer("adolar0042","https://avatars.githubusercontent.com/u/39769465?s=120&v=4","Contributor","https://github.com/adolar0042"),
        Developer("diegopyl1209","https://avatars.githubusercontent.com/u/80992641?s=120&v=4","Contributor","https://github.com/diegopyl1209"),
        Developer("sreekrishna2001","https://avatars.githubusercontent.com/u/67505103?s=120&v=4","Contributor","https://github.com/Sreekrishna2001"),
        Developer("riimuru","https://avatars.githubusercontent.com/u/57333995?s=120&v=4","Contributor","https://github.com/riimuru"),
        Developer("vu nguyen","https://avatars.githubusercontent.com/u/68330291?s=120&v=4","Contributor","https://github.com/hoangvu12"),
        Developer("animejeff","https://avatars.githubusercontent.com/u/101831300?s=120&v=4","rotubirtnoC","https://github.com/AnimeJeff"),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetDevelopersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.devsRecyclerView.adapter = DevelopersAdapter(developers)
        binding.devsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}