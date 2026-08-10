/**
 * Persistence adapter for the notes feature. The database is a trust boundary:
 * rows are parsed into domain records exactly once, in the row mapper here.
 */
@NullMarked
package com.example.demo.notes.persistence;

import org.jspecify.annotations.NullMarked;
