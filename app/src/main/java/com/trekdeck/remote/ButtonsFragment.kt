package com.trekdeck.remote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.trekdeck.remote.databinding.FragmentButtonsBinding

/** Сетка кнопок: список приходит с ПК (buttons.json), нажатие -> {"cmd":"press_btn","id":...}. */
class ButtonsFragment : Fragment() {

    private var _b: FragmentButtonsBinding? = null
    private val b get() = _b!!

    private val adapter = ButtonsAdapter { id -> sendCmd("press_btn", "id" to id) }

    private val listener = object : TrekDeckClient.Listener {
        override fun onButtons(list: List<PcButton>) = show(list)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentButtonsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.list.layoutManager = GridLayoutManager(requireContext(), 2)
        b.list.adapter = adapter
        show(TrekDeck.client.buttons)
    }

    override fun onStart() {
        super.onStart()
        TrekDeck.client.addListener(listener)
        show(TrekDeck.client.buttons)
    }

    override fun onStop() {
        TrekDeck.client.removeListener(listener)
        super.onStop()
    }

    private fun show(list: List<PcButton>) {
        val bd = _b ?: return
        adapter.submit(list)
        bd.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}

class ButtonsAdapter(private val onClick: (String) -> Unit) :
    RecyclerView.Adapter<ButtonsAdapter.VH>() {

    private var items: List<PcButton> = emptyList()

    class VH(val button: MaterialButton) : RecyclerView.ViewHolder(button)

    fun submit(list: List<PcButton>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_button, parent, false)
        return VH(v as MaterialButton)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.button.text = item.title
        holder.button.setOnClickListener { onClick(item.id) }
    }

    override fun getItemCount() = items.size
}
