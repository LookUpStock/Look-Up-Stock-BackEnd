package com.lookupstock.backend.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markiert einen Controller-Parameter vom Typ User. Der angemeldete Nutzer wird aus dem
 * Header „Authorization: Bearer &lt;token&gt;“ gelesen (CurrentUserArgumentResolver); ohne gültiges Token 401.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
