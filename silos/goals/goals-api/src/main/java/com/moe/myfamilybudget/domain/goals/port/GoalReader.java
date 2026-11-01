package com.moe.myfamilybudget.domain.goals.port;

import java.util.List;

import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;

/**
 * Port de lecture pour le domaine Objectifs (RF-B00).
 */
public interface GoalReader {

    List<ObjectifModel> getGoals();
}