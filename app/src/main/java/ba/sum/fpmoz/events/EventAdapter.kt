package ba.sum.fpmoz.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private var events: List<Event>,
    private val layoutResId: Int,
    private val onDeleteClick: ((String) -> Unit)? = null, // Make onDeleteClick nullable
    private val onItemClick: ((Event) -> Unit)? = null // New click listener for item click
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventName: TextView = itemView.findViewById(R.id.event_name)
        val eventDate: TextView? = itemView.findViewById(R.id.event_date)
        val eventType: TextView? = itemView.findViewById(R.id.event_type)
        val eventLocation: TextView? = itemView.findViewById(R.id.event_location)
        val deleteButton: Button? = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutResId, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.eventName.text = event.name
        holder.eventDate?.text = event.date?.split(" ")?.getOrNull(0) // Display only date
        holder.eventType?.text = event.typeName
        holder.eventLocation?.text = event.locationName
        holder.deleteButton?.setOnClickListener {
            onDeleteClick?.invoke(event.id)
        }

        // Set click listener for the whole item view
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(event)
        }
    }

    override fun getItemCount(): Int = events.size

    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}