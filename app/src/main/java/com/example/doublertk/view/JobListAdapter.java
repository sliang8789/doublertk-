package com.example.doublertk.view;

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
    private SimpleDateFormat dateFormat;
    private OnManageClickListener onManageClickListener;

    public interface OnManageClickListener {
        void onManageClick(Job job, int position, View anchorView);
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

        // 设置作业时间
        String timeStr = dateFormat.format(new Date(job.getCreatedAt()));
        holder.tvJobTime.setText(timeStr);

        // 设置管理按钮点击事件
        holder.btnManage.setOnClickListener(v -> {
            if (onManageClickListener != null) {
                onManageClickListener.onManageClick(job, position, v);
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
        this.jobs.add(0, job); // 添加到开头
        notifyItemInserted(0);
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

    /**
     * 删除作业
     */
    public void removeJob(int position) {
        if (position >= 0 && position < jobs.size()) {
            this.jobs.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, jobs.size());
        }
    }

    /**
     * 设置管理按钮点击监听器
     */
    public void setOnManageClickListener(OnManageClickListener listener) {
        this.onManageClickListener = listener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvJobName;
        TextView tvJobTime;
        Button btnManage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvJobName = itemView.findViewById(R.id.tv_job_name);
            tvJobTime = itemView.findViewById(R.id.tv_job_time);
            btnManage = itemView.findViewById(R.id.btn_manage);
        }
    }
}



