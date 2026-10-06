package com.example.walllearn.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.walllearn.R
import com.example.walllearn.core.EssayRepository
import com.example.walllearn.databinding.FragmentEssaysBinding
import com.example.walllearn.databinding.DialogAddEssayBinding
import com.example.walllearn.databinding.ItemEssayBinding
import com.example.walllearn.databinding.ItemEssayHeaderBinding
import com.example.walllearn.model.Essay
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The 40 model essays grouped by theme, then the user's own essays. */
class EssaysFragment : Fragment() {

    private var _binding: FragmentEssaysBinding? = null
    private val binding get() = _binding!!
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val adapter = EssayAdapter(
        onOpen = { startActivity(EssayReaderActivity.intent(requireContext(), it.id)) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEssaysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.essayList.layoutManager = LinearLayoutManager(requireContext())
        binding.essayList.adapter = adapter
        binding.addEssayButton.setOnClickListener { showAddDialog() }
        reload()
    }

    override fun onDestroyView() {
        main.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun reload() {
        val appContext = requireContext().applicationContext
        executor.execute {
            val essays = EssayRepository.all(appContext)
            main.post { if (_binding != null) adapter.submit(essays) }
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddEssayBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_essay_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_add_word) { _, _ ->
                val added = EssayRepository.addUserEssay(
                    requireContext(),
                    dialogBinding.titleInput.text?.toString().orEmpty(),
                    dialogBinding.promptInput.text?.toString().orEmpty(),
                    dialogBinding.bodyInput.text?.toString().orEmpty()
                )
                if (added == null) {
                    Toast.makeText(requireContext(), R.string.add_essay_empty, Toast.LENGTH_SHORT).show()
                } else {
                    reload()
                    startActivity(EssayReaderActivity.intent(requireContext(), added.id))
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmDelete(essay: Essay) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_essay_title)
            .setMessage(essay.title)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                EssayRepository.deleteUserEssay(requireContext(), essay.id)
                reload()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private sealed interface Row {
        data class Header(val title: String) : Row
        data class Item(val essay: Essay) : Row
    }

    private class EssayAdapter(
        private val onOpen: (Essay) -> Unit,
        private val onDelete: (Essay) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var rows: List<Row> = emptyList()

        fun submit(essays: List<Essay>) {
            // Group by theme in first-seen order; the user's own essays are already last.
            val built = ArrayList<Row>()
            for ((theme, group) in essays.groupBy { it.theme }) {
                built.add(Row.Header(theme))
                group.forEach { built.add(Row.Item(it)) }
            }
            rows = built
            notifyDataSetChanged()
        }

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderHolder(ItemEssayHeaderBinding.inflate(inflater, parent, false))
            } else {
                ItemHolder(ItemEssayBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).binding.root.text = row.title
                is Row.Item -> {
                    val b = (holder as ItemHolder).binding
                    val e = row.essay
                    b.essayTitle.text = e.title
                    b.essayMeta.text = if (e.instructionType.isBlank()) {
                        b.root.context.getString(R.string.essay_meta_user_format, e.wordCount)
                    } else {
                        b.root.context.getString(R.string.essay_meta_format, e.instructionType, e.wordCount)
                    }
                    b.root.setOnClickListener { onOpen(e) }
                    if (e.isUser) {
                        b.root.setOnLongClickListener { onDelete(e); true }
                    } else {
                        b.root.setOnLongClickListener(null)
                        b.root.isLongClickable = false
                    }
                }
            }
        }

        class HeaderHolder(val binding: ItemEssayHeaderBinding) : RecyclerView.ViewHolder(binding.root)
        class ItemHolder(val binding: ItemEssayBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
