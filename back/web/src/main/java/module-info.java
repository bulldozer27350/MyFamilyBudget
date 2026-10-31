module com.moe.myfamilybudget.web {
    requires com.moe.myfamilybudget.application.api;
    requires com.moe.myfamilybudget.application;

    requires com.moe.myfamilybudget.domain.bankpointage;
    requires com.moe.myfamilybudget.domain.credit;
    requires com.moe.myfamilybudget.domain.goals;
    requires com.moe.myfamilybudget.domain.market;
    requires com.moe.myfamilybudget.domain.notifications;
    requires com.moe.myfamilybudget.domain.retirement;
    requires com.moe.myfamilybudget.domain.settings;
    requires com.moe.myfamilybudget.domain.tax;
    requires com.moe.myfamilybudget.domain.treasury;
    requires com.moe.myfamilybudget.domain.wealth;

    requires api;

    requires spring.beans;
    requires spring.context;
    requires spring.web;
    requires spring.webmvc;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires jakarta.servlet;
    requires jakarta.validation;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;

    exports com.moe.myfamilybudget.web.config;
    exports com.moe.myfamilybudget.web.controller;
    exports com.moe.myfamilybudget.web.error;
    exports com.moe.myfamilybudget.web.mapper;
    exports com.moe.myfamilybudget.web.service;
}
