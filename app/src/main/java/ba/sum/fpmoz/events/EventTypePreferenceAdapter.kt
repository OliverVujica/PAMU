package ba.sum.fpmoz.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class EventTypePreference(
    val id: String,
    val name: String,
    var isChecked: Boolean = false
)

class EventTypePreferenceAdapter(
    private var eventTypePreferences: MutableList<EventTypePreference>
) : RecyclerView.Adapter<EventTypePreferenceAdapter.EventTypePreferenceViewHolder>() {

    class EventTypePreferenceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.event_type_preference_name)
        val checkBox: CheckBox = itemView.findViewById(R.id.event_type_preference_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventTypePreferenceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_type_preference, parent, false)
        return EventTypePreferenceViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventTypePreferenceViewHolder, position: Int) {
        val preference = eventTypePreferences[position]
        holder.nameTextView.text = preference.name
        holder.checkBox.isChecked = preference.isChecked

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            preference.isChecked = isChecked
        }
        holder.itemView.setOnClickListener {
            holder.checkBox.toggle()
        }
    }

    override fun getItemCount(): Int = eventTypePreferences.size

    fun updateData(newEventTypes: List<ba.sum.fpmoz.events.EventType>, preferredIds: Set<String>) {
        eventTypePreferences.clear()
        newEventTypes.forEach { eventType ->
            eventTypePreferences.add(
                EventTypePreference(
                    id = eventType.id,
                    name = eventType.name,
                    isChecked = preferredIds.contains(eventType.id)
                )
            )
        }
        notifyDataSetChanged()
    }

    fun getSelectedTypeIds(): List<String> {
        return eventTypePreferences.filter { it.isChecked }.map { it.id }
    }
}