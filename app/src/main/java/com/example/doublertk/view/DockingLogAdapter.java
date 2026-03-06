package com.example.doublertk.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doublertk.R;
import com.example.doublertk.data.DockingLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DockingLogAdapter extends RecyclerView.Adapter<DockingLogAdapter.ViewHolder> {

    private final Context context;
    private List<DockingLog> logs = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public DockingLogAdapter(Context context) {
        this.context = context;
    }

    public void setLogs(List<DockingLog> logs) {
        this.logs = logs != null ? logs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_docking_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DockingLog log = logs.get(position);

        holder.tvJobName.setText(log.getJobName() != null ? log.getJobName() : "未命名作业");

        if (log.isSuccess()) {
            holder.tvStatus.setText("成功");
            holder.tvStatus.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
        } else if (log.getStatus().equals("IN_PROGRESS")) {
            holder.tvStatus.setText("进行中");
            holder.tvStatus.setTextColor(context.getResources().getColor(android.R.color.holo_blue_dark));
        } else if (log.getStatus().equals("CANCELLED")) {
            holder.tvStatus.setText("已取消");
            holder.tvStatus.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
        } else {
            holder.tvStatus.setText("失败");
            holder.tvStatus.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
        }

        holder.tvTime.setText(dateFormat.format(new Date(log.getStartTime())));
        holder.tvDuration.setText(log.getFormattedDuration());
        holder.tvBowError.setText(String.format(Locale.getDefault(), "%.2fm", log.getFinalBowError()));
        holder.tvSternError.setText(String.format(Locale.getDefault(), "%.2fm", log.getFinalSternError()));
        holder.tvHeadingError.setText(String.format(Locale.getDefault(), "%.1f°", log.getFinalHeadingError()));

        String rating = log.getAccuracyRating();
        holder.tvAccuracyRating.setText("精度：" + rating);

        int ratingColor;
        switch (rating) {
            case "优秀":
                ratingColor = context.getResources().getColor(android.R.color.holo_green_dark);
                break;
            case "良好":
                ratingColor = context.getResources().getColor(android.R.color.holo_blue_dark);
                break;
            case "一般":
                ratingColor = context.getResources().getColor(android.R.color.holo_orange_dark);
                break;
            default:
                ratingColor = context.getResources().getColor(android.R.color.holo_red_dark);
                break;
        }
        holder.tvAccuracyRating.setTextColor(ratingColor);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvJobName;
        TextView tvStatus;
        TextView tvTime;
        TextView tvDuration;
        TextView tvBowError;
        TextView tvSternError;
        TextView tvHeadingError;
        TextView tvAccuracyRating;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvJobName = itemView.findViewById(R.id.tv_job_name);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvBowError = itemView.findViewById(R.id.tv_bow_error);
            tvSternError = itemView.findViewById(R.id.tv_stern_error);
            tvHeadingError = itemView.findViewById(R.id.tv_heading_error);
            tvAccuracyRating = itemView.findViewById(R.id.tv_accuracy_rating);
        }
    }
}
