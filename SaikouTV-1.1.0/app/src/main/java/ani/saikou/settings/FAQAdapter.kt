package ani.saikou.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.databinding.ItemQuestionBinding
import ani.saikou.loadData
import ani.saikou.others.CustomBottomDialog
import ani.saikou.setAnimation
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin

class FAQAdapter(private val questions: List<Triple<Int, String, String>>, private val manager: FragmentManager) :
    RecyclerView.Adapter<FAQAdapter.FAQViewHolder>() {
    private val uiSettings = loadData<UserInterfaceSettings>("ui_settings") ?: UserInterfaceSettings()

    inner class FAQViewHolder(val binding: ItemQuestionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FAQViewHolder {
        return FAQViewHolder(ItemQuestionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: FAQViewHolder, position: Int) {
        val b = holder.binding.root
        setAnimation(b.context, b, uiSettings)
        val dev = questions[position]
        b.text = dev.second
        b.setCompoundDrawablesWithIntrinsicBounds(dev.first, 0, 0, 0)
        b.setOnClickListener {
            CustomBottomDialog.newInstance().apply {
                setTitleText(dev.second)
                addView(
                    TextView(b.context).apply {
                        val markWon = Markwon.builder(b.context).usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                        markWon.setMarkdown(this, dev.third)
                    }
                )
            }.show(manager, "dialog")
        }
    }

    override fun getItemCount(): Int = questions.size
}