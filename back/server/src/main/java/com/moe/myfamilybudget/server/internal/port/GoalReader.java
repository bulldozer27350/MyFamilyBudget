package com.moe.myfamilybudget.server.internal.port;

import java.util.List;

import com.moe.myfamilybudget.server.internal.model.ObjectifModel;

/**
 * Port de lecture pour le domaine Objectifs (RF-B00).
 */
public interface GoalReader {

    List<ObjectifModel> getGoals();
}