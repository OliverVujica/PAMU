package ba.sum.fpmoz.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class EventType(
    val id: String,
    val name: String
)

class EventTypeAdapter(
    private var eventTypes: List<EventType>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<EventTypeAdapter.EventTypeViewHolder>() {

    class EventTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventTypeName: TextView = itemView.findViewById(R.id.event_type_name)
        val deleteButton: Button = itemView.findViewById(R.id.btnDeleteEventType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventTypeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_type, parent, false)
        return EventTypeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventTypeViewHolder, position: Int) {
        val eventType = eventTypes[position]
        holder.eventTypeName.text = eventType.name
        holder.deleteButton.setOnClickListener {
            onDeleteClick(eventType.id)
        }
    }

    override fun getItemCount(): Int = eventTypes.size

    fun updateEventTypes(newEventTypes: List<EventType>) {
        eventTypes = newEventTypes
        notifyDataSetChanged()
    }
}