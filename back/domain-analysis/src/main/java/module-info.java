module com.moe.myfamilybudget.domain.analysis {
    requires com.moe.myfamilybudget.domain.budget;
    requires transitive com.moe.myfamilybudget.domain.bankpointage;

    exports com.moe.myfamilybudget.domain.analysis.calculation;
    exports com.moe.myfamilybudget.domain.analysis.model;
}
