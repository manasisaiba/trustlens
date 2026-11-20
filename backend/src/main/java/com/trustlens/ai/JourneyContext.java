package com.trustlens.ai;

import com.trustlens.domain.Business;
import com.trustlens.domain.JourneyTask;
import com.trustlens.domain.Persona;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Mutable state of one persona journey, shared with the planner. */
public class JourneyContext {
    public final Business business;
    public final Persona persona;
    public final JourneyTask task;
    public final List<String> lookFor;
    public final int maxActions;
    public final List<String> history = new ArrayList<>();
    public final Set<String> visitedUrls = new HashSet<>();
    public final Set<String> matchedKeywords = new HashSet<>();
    public final Set<String> clicked = new HashSet<>();
    public int step = 0;

    public JourneyContext(Business business, Persona persona, JourneyTask task, List<String> lookFor, int maxActions) {
        this.business = business;
        this.persona = persona;
        this.task = task;
        this.lookFor = lookFor;
        this.maxActions = maxActions;
    }
}
