package com.example.doublertk.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.doublertk.R;
import com.example.doublertk.data.CoordinateSystem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 参数系统列表适配器
 */
public class ParameterSystemAdapter extends RecyclerView.Adapter<ParameterSystemAdapter.ViewHolder> {

    private Context context;
    private List<CoordinateSystem> coordinateSystems;
    private List<Integer> knownPointCounts;
    private int selectedPosition = -1;
    private long currentSystemId = -1; // 当前使用的坐标系ID
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private OnMoreOptionsClickListener onMoreOptionsClickListener;
    private SimpleDateFormat dateFormat;

    public interface OnItemClickListener {
        void onItemClick(CoordinateSystem coordinateSystem, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(CoordinateSystem coordinateSystem, int position);
    }

    public interface OnMoreOptionsClickListener {
        void onMoreOptionsClick(CoordinateSystem coordinateSystem, int position, View anchorView);
    }

    public ParameterSystemAdapter(Context context) {
        this.context = context;
        this.coordinateSystems = new ArrayList<>();
        this.knownPointCounts = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_parameter_system, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CoordinateSystem system = coordinateSystems.get(position);
        int pointCount = knownPointCounts.size() > position ? knownPointCounts.get(position) : 0;

        // 设置坐标系名称
        holder.tvSystemName.setText(system.getName());

        // 设置创建时间
        String timeStr = dateFormat.format(new Date(system.getCreatedAt()));
        holder.tvCreateTime.setText(timeStr);

        // 设置椭球类型
        holder.tvEllipsoidType.setText(system.getEllipsoidName());

        // 设置投影类型
        holder.tvProjectionType.setText(system.getProjectionName());

        // 设置已知点数量
        holder.tvPointCount.setText(pointCount + "点");

        // 设置选中状态
        boolean isSelected = (position == selectedPosition);
        holder.ivSelectedIndicator.setImageResource(
                isSelected ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked
        );

        // 设置当前使用状态
        boolean isCurrentSystem = (system.getId() == currentSystemId);
        if (isCurrentSystem) {
            holder.tvCurrentIndicator.setVisibility(View.VISIBLE);
            holder.tvCurrentIndicator.setText("当前使用");
            holder.tvCurrentIndicator.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
        } else {
            holder.tvCurrentIndicator.setVisibility(View.GONE);
        }

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            int oldPosition = selectedPosition;
            selectedPosition = position;

            // 刷新旧的和新的选中项
            if (oldPosition != -1) {
                notifyItemChanged(oldPosition);
            }
            notifyItemChanged(position);

            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(system, position);
            }
        });

        // 设置长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (onItemLongClickListener != null) {
                onItemLongClickListener.onItemLongClick(system, position);
                return true;
            }
            return false;
        });

        // 设置更多选项点击事件
        holder.ivMoreOptions.setOnClickListener(v -> {
            if (onMoreOptionsClickListener != null) {
                onMoreOptionsClickListener.onMoreOptionsClick(system, position, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return coordinateSystems.size();
    }

    /**
     * 更新数据
     */
    public void updateData(List<CoordinateSystem> systems, List<Integer> pointCounts) {
        this.coordinateSystems.clear();
        this.coordinateSystems.addAll(systems);

        this.knownPointCounts.clear();
        if (pointCounts != null) {
            this.knownPointCounts.addAll(pointCounts);
        }

        // 查找当前活动的坐标系（暂时不设置选中项）
        selectedPosition = -1;

        notifyDataSetChanged();
    }

    /**
     * 设置当前使用的坐标系ID
     */
    public void setCurrentSystemId(long systemId) {
        this.currentSystemId = systemId;
        notifyDataSetChanged();
    }

    /**
     * 获取当前使用的坐标系ID
     */
    public long getCurrentSystemId() {
        return currentSystemId;
    }

    /**
     * 获取选中的坐标系
     */
    public CoordinateSystem getSelectedSystem() {
        if (selectedPosition >= 0 && selectedPosition < coordinateSystems.size()) {
            return coordinateSystems.get(selectedPosition);
        }
        return null;
    }

    /**
     * 获取选中位置
     */
    public int getSelectedPosition() {
        return selectedPosition;
    }

    /**
     * 设置选中位置
     */
    public void setSelectedPosition(int position) {
        int oldPosition = selectedPosition;
        selectedPosition = position;

        if (oldPosition != -1) {
            notifyItemChanged(oldPosition);
        }
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    /**
     * 获取指定位置的坐标系
     */
    public CoordinateSystem getItem(int position) {
        if (position >= 0 && position < coordinateSystems.size()) {
            return coordinateSystems.get(position);
        }
        return null;
    }

    /**
     * 删除指定位置的项目
     */
    public void removeItem(int position) {
        if (position >= 0 && position < coordinateSystems.size()) {
            coordinateSystems.remove(position);
            if (position < knownPointCounts.size()) {
                knownPointCounts.remove(position);
            }

            // 调整选中位置
            if (selectedPosition == position) {
                selectedPosition = -1;
            } else if (selectedPosition > position) {
                selectedPosition--;
            }

            notifyItemRemoved(position);
            notifyItemRangeChanged(position, coordinateSystems.size() - position);
        }
    }

    /**
     * 添加新项目
     */
    public void addItem(CoordinateSystem system, int pointCount) {
        coordinateSystems.add(system);
        knownPointCounts.add(pointCount);
        notifyItemInserted(coordinateSystems.size() - 1);
    }

    /**
     * 更新指定位置的项目
     */
    public void updateItem(int position, CoordinateSystem system, int pointCount) {
        if (position >= 0 && position < coordinateSystems.size()) {
            coordinateSystems.set(position, system);
            if (position < knownPointCounts.size()) {
                knownPointCounts.set(position, pointCount);
            } else {
                knownPointCounts.add(pointCount);
            }
            notifyItemChanged(position);
        }
    }

    // 设置监听器
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }

    public void setOnMoreOptionsClickListener(OnMoreOptionsClickListener listener) {
        this.onMoreOptionsClickListener = listener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivSelectedIndicator;
        TextView tvSystemName;
        TextView tvCreateTime;
        TextView tvEllipsoidType;
        TextView tvProjectionType;
        TextView tvPointCount;
        TextView tvCurrentIndicator; // 新增：当前使用指示器
        Button ivMoreOptions;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSelectedIndicator = itemView.findViewById(R.id.iv_selected_indicator);
            tvSystemName = itemView.findViewById(R.id.tv_system_name);
            tvCreateTime = itemView.findViewById(R.id.tv_create_time);
            tvEllipsoidType = itemView.findViewById(R.id.tv_ellipsoid_type);
            tvProjectionType = itemView.findViewById(R.id.tv_projection_type);
            tvPointCount = itemView.findViewById(R.id.tv_point_count);
            tvCurrentIndicator = itemView.findViewById(R.id.tv_current_indicator); // 新增
            ivMoreOptions = itemView.findViewById(R.id.iv_more_options);
        }
    }
}