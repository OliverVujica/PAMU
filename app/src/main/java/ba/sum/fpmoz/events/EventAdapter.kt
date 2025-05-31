package ba.sum.fpmoz.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private var events: List<Event>,
    private val layoutResId: Int,
    private val onDeleteClick: ((String) -> Unit)? = null,
    private val onItemClick: ((Event) -> Unit)? = null,
    private val onInterestClick: ((Event, Int) -> Unit)? = null // Callback za klik na "zainteresiran"
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventName: TextView = itemView.findViewById(R.id.event_name)
        val eventDate: TextView? = itemView.findViewById(R.id.event_date) // Može biti null ako se koristi item_event.xml
        val eventType: TextView? = itemView.findViewById(R.id.event_type) // Može biti null
        val eventLocation: TextView? = itemView.findViewById(R.id.event_location) // Može biti null
        val deleteButton: Button? = itemView.findViewById(R.id.btnDelete) // Može biti null

        // Novi View elementi za "zainteresiran sam" (samo za item_event_welcome.xml)
        val interestedIcon: ImageView? = itemView.findViewById(R.id.interested_icon)
        val interestedCountText: TextView? = itemView.findViewById(R.id.interested_count_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutResId, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.eventName.text = event.name

        // Postavljanje datuma, tipa, lokacije ako postoje u layoutu
        holder.eventDate?.text = event.date.split(" ").getOrNull(0)
        holder.eventType?.text = event.typeName
        holder.eventLocation?.text = event.locationName

        holder.deleteButton?.setOnClickListener {
            onDeleteClick?.invoke(event.id)
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(event)
        }

        // Postavljanje za "zainteresiran sam" ako View elementi postoje
        if (holder.interestedIcon != null && holder.interestedCountText != null) {
            if (event.isCurrentUserInterested) {
                holder.interestedIcon.setImageResource(R.drawable.ic_star_filled)
            } else {
                holder.interestedIcon.setImageResource(R.drawable.ic_star_border)
            }
            holder.interestedCountText.text = "${event.interestedCount} interested"

            holder.interestedIcon.setOnClickListener {
                onInterestClick?.invoke(event, position)
            }
        } else {
            // Ako se koristi layout koji nema ove elemente (npr. item_event.xml), sakrij ih ili nemoj ništa raditi
            holder.interestedIcon?.visibility = View.GONE
            holder.interestedCountText?.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = events.size

    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}