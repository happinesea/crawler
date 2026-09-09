package com.happinesea.webcrawler.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;

class CategoryAssignmentPlanTest {

	@Test
	void partitionsApprovedExactCases() {
		assertEquals(List.of(3, 3, 4), assignmentSizes(10, 3));
		assertEquals(List.of(3, 3, 3, 5), assignmentSizes(14, 4));
		assertEquals(List.of(3, 3, 3, 3), assignmentSizes(12, 4));
		assertEquals(List.of(1), assignmentSizes(1, 1));
		assertEquals(List.of(0, 0, 0, 0, 3), assignmentSizes(3, 5));
	}

	@Test
	void assignmentsAreCompleteDisjointDeterministicAndSorted() {
		List<SiteInfoProcessPool> shuffled = pools(8, 3, 5, 1, 7, 2, 6, 4);

		CategoryAssignmentPlan first = CategoryAssignmentPlan.create(shuffled, 3);
		CategoryAssignmentPlan second = CategoryAssignmentPlan.create(new ArrayList<>(shuffled), 3);

		assertEquals(List.of(
				List.of(1, 2),
				List.of(3, 4),
				List.of(5, 6, 7, 8)), categoryIds(first));
		assertEquals(categoryIds(first), categoryIds(second));

		Set<Integer> union = new HashSet<>();
		for (CategoryAssignmentPlan.CategoryAssignment assignment : first.assignments()) {
			Set<Integer> ids = new HashSet<>(assignment.categoryIds());
			assertEquals(assignment.categories().size(), ids.size());
			for (Integer id : ids) {
				if (!union.add(id)) {
					throw new AssertionError("category assigned more than once: " + id);
				}
			}
		}
		assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8), union);
	}

	@Test
	void retainsEmptyLogicalAssignmentsButSubmitsOnlyNonemptyAssignments() {
		CategoryAssignmentPlan plan = CategoryAssignmentPlan.create(pools(3, 1, 2), 5);

		assertEquals(5, plan.assignments().size());
		assertEquals(List.of(1, 2, 3, 4, 5),
				plan.assignments().stream().map(CategoryAssignmentPlan.CategoryAssignment::workerNumber).toList());
		assertEquals(1, plan.nonEmptyAssignments().size());
		assertEquals(5, plan.nonEmptyAssignments().get(0).workerNumber());
		assertEquals(List.of(1, 2, 3), plan.nonEmptyAssignments().get(0).categoryIds());
	}

	@Test
	void assignmentListsAreImmutableSnapshots() {
		List<SiteInfoProcessPool> source = new ArrayList<>(pools(1, 2, 3));
		CategoryAssignmentPlan plan = CategoryAssignmentPlan.create(source, 2);
		source.clear();

		assertEquals(List.of(List.of(1), List.of(2, 3)), categoryIds(plan));
		assertThrows(UnsupportedOperationException.class,
				() -> plan.assignments().add(plan.assignments().get(0)));
		assertThrows(UnsupportedOperationException.class,
				() -> plan.assignments().get(0).categories().clear());
	}

	private List<Integer> assignmentSizes(int categoryCount, int workerCount) {
		int[] ids = new int[categoryCount];
		for (int i = 0; i < categoryCount; i++) {
			ids[i] = categoryCount - i;
		}
		return CategoryAssignmentPlan.create(pools(ids), workerCount).assignments().stream()
				.map(assignment -> assignment.categories().size())
				.toList();
	}

	private List<List<Integer>> categoryIds(CategoryAssignmentPlan plan) {
		return plan.assignments().stream()
				.map(CategoryAssignmentPlan.CategoryAssignment::categoryIds)
				.toList();
	}

	private List<SiteInfoProcessPool> pools(int... categoryIds) {
		List<SiteInfoProcessPool> pools = new ArrayList<>();
		for (int categoryId : categoryIds) {
			SiteCategory category = new SiteCategory();
			category.setSiteCategoryId(categoryId);
			SiteInfoProcessPool pool = new SiteInfoProcessPool();
			pool.setSiteInfoProcessId(1000 + categoryId);
			pool.setSiteCategory(category);
			pools.add(pool);
		}
		return pools;
	}
}
