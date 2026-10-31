module api {
    requires com.fasterxml.jackson.annotation;
    requires io.swagger.v3.oas.annotations;
    requires jakarta.annotation;
    requires jakarta.servlet;
    requires jakarta.validation;
    requires spring.context;
    requires spring.web;

    exports com.moe.myfamilybudget.api.controller;
    exports com.moe.myfamilybudget.api.model;
}
