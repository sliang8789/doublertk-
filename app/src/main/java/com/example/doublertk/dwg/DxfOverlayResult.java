package com.example.doublertk.dwg;

import java.util.List;

/**
 * DXF 网格覆盖层结果：点与连线
 */
public class DxfOverlayResult {
	private List<float[]> points; // 每项: {north, east, flag}
	private List<int[]> links;    // 每项: {i, j}

	public DxfOverlayResult(List<float[]> points, List<int[]> links) {
		this.points = points;
		this.links = links;
	}

	public List<float[]> getPoints() {
		return points;
	}

	public void setPoints(List<float[]> points) {
		this.points = points;
	}

	public List<int[]> getLinks() {
		return links;
	}

	public void setLinks(List<int[]> links) {
		this.links = links;
	}
}

