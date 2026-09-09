package com.happinesea.webcrawler.config;

import java.util.Comparator;
import java.util.List;

import com.happinesea.webcrawler.entity.SiteInfoProcessPool;

public record CategoryAssignmentPlan(List<CategoryAssignment> assignments) {

	public CategoryAssignmentPlan {
		assignments = List.copyOf(assignments);
	}

	public static CategoryAssignmentPlan create(List<SiteInfoProcessPool> eligibleCategories,
			int logicalWorkerCount) {
		if (logicalWorkerCount <= 0) {
			throw new IllegalArgumentException("logicalWorkerCount must be positive");
		}

		List<SiteInfoProcessPool> sortedCategories = List.copyOf(eligibleCategories).stream()
				.sorted(Comparator.comparing(pool -> pool.getSiteCategory().getSiteCategoryId()))
				.toList();
		int baseAssignmentSize = sortedCategories.size() / logicalWorkerCount;
		int fromIndex = 0;
		var assignments = new java.util.ArrayList<CategoryAssignment>(logicalWorkerCount);
		for (int workerNumber = 1; workerNumber <= logicalWorkerCount; workerNumber++) {
			int assignmentSize = workerNumber == logicalWorkerCount
					? sortedCategories.size() - fromIndex
					: baseAssignmentSize;
			int toIndex = fromIndex + assignmentSize;
			assignments.add(new CategoryAssignment(workerNumber,
					sortedCategories.subList(fromIndex, toIndex)));
			fromIndex = toIndex;
		}
		return new CategoryAssignmentPlan(assignments);
	}

	public List<CategoryAssignment> nonEmptyAssignments() {
		return assignments.stream()
				.filter(assignment -> !assignment.categories().isEmpty())
				.toList();
	}

	public record CategoryAssignment(int workerNumber, List<SiteInfoProcessPool> categories) {
		public CategoryAssignment {
			if (workerNumber <= 0) {
				throw new IllegalArgumentException("workerNumber must be positive");
			}
			categories = List.copyOf(categories);
		}

		public List<Integer> categoryIds() {
			return categories.stream()
					.map(pool -> pool.getSiteCategory().getSiteCategoryId())
					.toList();
		}
	}
}
