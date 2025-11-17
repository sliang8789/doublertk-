package com.example.doublertk.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.model.JobItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JobListAdapter extends RecyclerView.Adapter<JobListAdapter.JobViewHolder> {

    public interface OnJobEditClickListener {
        void onJobEditClick(JobItem item, int position);
    }

    private final List<JobItem> jobs = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private OnJobEditClickListener onJobEditClickListener;

    @NonNull
    @Override
    public JobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_job_entry, parent, false);
        return new JobViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull JobViewHolder holder, int position) {
        JobItem job = jobs.get(position);
        holder.jobName.setText(job.getName());
        holder.jobTime.setText(dateFormat.format(new Date(job.getTimestamp())));

        holder.jobEdit.setOnClickListener(v -> {
            if (onJobEditClickListener != null) {
                onJobEditClickListener.onJobEditClick(job, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return jobs.size();
    }

    public void setItems(List<JobItem> newJobs) {
        jobs.clear();
        if (newJobs != null) {
            jobs.addAll(newJobs);
        }
        notifyDataSetChanged();
    }

    public void addItem(JobItem item) {
        jobs.add(0, item);
        notifyItemInserted(0);
    }

    public void updateItem(int position, JobItem item) {
        if (position < 0 || position >= jobs.size()) return;
        jobs.set(position, item);
        notifyItemChanged(position);
    }

    public void removeItem(int position) {
        if (position < 0 || position >= jobs.size()) return;
        jobs.remove(position);
        notifyItemRemoved(position);
    }

    public JobItem getItem(int position) {
        if (position < 0 || position >= jobs.size()) return null;
        return jobs.get(position);
    }

    public void setOnJobEditClickListener(OnJobEditClickListener listener) {
        this.onJobEditClickListener = listener;
    }

    static class JobViewHolder extends RecyclerView.ViewHolder {
        final TextView jobName;
        final TextView jobTime;
        final TextView jobEdit;

        JobViewHolder(@NonNull View itemView) {
            super(itemView);
            jobName = itemView.findViewById(R.id.text_job_name);
            jobTime = itemView.findViewById(R.id.text_job_time);
            jobEdit = itemView.findViewById(R.id.text_job_edit);
        }
    }
}

