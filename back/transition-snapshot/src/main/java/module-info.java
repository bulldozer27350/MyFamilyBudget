module com.moe.myfamilybudget.transition {
    requires transitive com.moe.myfamilybudget.domain.budget;
    requires transitive com.moe.myfamilybudget.domain.retirement;
    requires transitive com.moe.myfamilybudget.domain.tax;
    requires transitive com.moe.myfamilybudget.domain.wealth;
    requires transitive com.moe.myfamilybudget.domain.bankpointage;
    requires transitive com.moe.myfamilybudget.domain.credit;
    requires transitive com.moe.myfamilybudget.domain.goals;

    exports com.moe.myfamilybudget.transition.error;
    exports com.moe.myfamilybudget.transition.model;
    exports com.moe.myfamilybudget.transition.port;
}
