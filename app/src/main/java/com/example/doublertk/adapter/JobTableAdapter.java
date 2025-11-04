package com.example.doublertk.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.model.JobItem;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 作业表格适配器
 */
public class JobTableAdapter extends RecyclerView.Adapter<JobTableAdapter.JobViewHolder> {

    private List<JobItem> jobList;
    private OnEditClickListener onEditClickListener;

    public interface OnEditClickListener {
        void onEditClick(int position);
    }

    public JobTableAdapter(List<JobItem> jobList) {
        this.jobList = jobList;
    }

    public void setOnEditClickListener(OnEditClickListener listener) {
        this.onEditClickListener = listener;
    }

    @NonNull
    @Override
    public JobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_job_table_row, parent, false);
        return new JobViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JobViewHolder holder, int position) {
        JobItem job = jobList.get(position);
        holder.bind(job, position);
    }

    @Override
    public int getItemCount() {
        return jobList != null ? jobList.size() : 0;
    }

    /**
     * 更新作业列表
     */
    public void updateJobList(List<JobItem> newJobList) {
        this.jobList = newJobList;
        notifyDataSetChanged();
    }

    /**
     * 添加作业
     */
    public void addJob(JobItem job) {
        if (jobList != null) {
            jobList.add(job);
            notifyItemInserted(jobList.size() - 1);
        }
    }

    /**
     * 删除作业
     */
    public void removeJob(int position) {
        if (jobList != null && position >= 0 && position < jobList.size()) {
            jobList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, jobList.size());
        }
    }

    /**
     * 更新作业
     */
    public void updateJob(int position, JobItem job) {
        if (jobList != null && position >= 0 && position < jobList.size()) {
            jobList.set(position, job);
            notifyItemChanged(position);
        }
    }

    /**
     * ViewHolder类
     */
    class JobViewHolder extends RecyclerView.ViewHolder {
        private TextView tvJobName;
        private TextView tvJobTime;
        private MaterialButton btnEdit;

        public JobViewHolder(@NonNull View itemView) {
            super(itemView);
            tvJobName = itemView.findViewById(R.id.tv_job_name);
            tvJobTime = itemView.findViewById(R.id.tv_job_time);
            btnEdit = itemView.findViewById(R.id.btn_row_edit);

            // 编辑按钮点击事件
            btnEdit.setOnClickListener(v -> {
                if (onEditClickListener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onEditClickListener.onEditClick(position);
                    }
                }
            });
        }

        public void bind(JobItem job, int position) {
            tvJobName.setText(job.getJobName());
            tvJobTime.setText(job.getJobTime());
        }
    }
}

