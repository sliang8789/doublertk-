package com.example.doublertk.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.data.Job;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 作业列表适配器
 */
public class JobListAdapter extends RecyclerView.Adapter<JobListAdapter.ViewHolder> {

    private Context context;
    private List<Job> jobs;
    private OnItemClickListener onItemClickListener;
    private OnEditClickListener onEditClickListener;
    private OnDeleteClickListener onDeleteClickListener;
    private SimpleDateFormat dateFormat;

    public interface OnItemClickListener {
        void onItemClick(Job job, int position);
    }

    public interface OnEditClickListener {
        void onEditClick(Job job, int position);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Job job, int position);
    }

    public JobListAdapter(Context context) {
        this.context = context;
        this.jobs = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_job, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Job job = jobs.get(position);

        // 设置作业名称
        holder.tvJobName.setText(job.getName());

        // 设置创建时间
        String timeStr = dateFormat.format(new Date(job.getCreatedAt()));
        holder.tvCreateTime.setText(timeStr);

        // 设置描述（如果有）
        if (job.getDescription() != null && !job.getDescription().isEmpty()) {
            holder.tvDescription.setText(job.getDescription());
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(job, position);
            }
        });

        // 设置编辑按钮点击事件
        holder.btnEdit.setOnClickListener(v -> {
            if (onEditClickListener != null) {
                onEditClickListener.onEditClick(job, position);
            }
        });

        // 设置删除按钮点击事件
        holder.btnDelete.setOnClickListener(v -> {
            if (onDeleteClickListener != null) {
                onDeleteClickListener.onDeleteClick(job, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return jobs.size();
    }

    /**
     * 更新数据
     */
    public void updateData(List<Job> jobList) {
        this.jobs.clear();
        if (jobList != null) {
            this.jobs.addAll(jobList);
        }
        notifyDataSetChanged();
    }

    /**
     * 添加作业
     */
    public void addJob(Job job) {
        this.jobs.add(0, job);
        notifyItemInserted(0);
    }

    /**
     * 移除作业
     */
    public void removeJob(int position) {
        if (position >= 0 && position < jobs.size()) {
            this.jobs.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, jobs.size() - position);
        }
    }

    /**
     * 更新作业
     */
    public void updateJob(int position, Job job) {
        if (position >= 0 && position < jobs.size()) {
            this.jobs.set(position, job);
            notifyItemChanged(position);
        }
    }

    // 设置监听器
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnEditClickListener(OnEditClickListener listener) {
        this.onEditClickListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.onDeleteClickListener = listener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvJobName;
        TextView tvCreateTime;
        TextView tvDescription;
        Button btnEdit;
        Button btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvJobName = itemView.findViewById(R.id.tv_job_name);
            tvCreateTime = itemView.findViewById(R.id.tv_create_time);
            tvDescription = itemView.findViewById(R.id.tv_description);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}

