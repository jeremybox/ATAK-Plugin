package com.atakmap.android.meshtastic.util;

import android.content.SharedPreferences;
import com.atakmap.coremap.log.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe state manager for handling SharedPreferences state transitions
 * with automatic rollback support on errors
 */
public class StateManager {
    private static final String TAG = "StateManager";
    private final SharedPreferences prefs;
    private final ReentrantLock lock = new ReentrantLock();
    
    // Transaction tracking
    private static class Transaction {
        final Map<String, Object> originalValues = new HashMap<>();
        final Map<String, Object> newValues = new HashMap<>();
        boolean committed = false;
    }
    
    private final ThreadLocal<Transaction> currentTransaction = new ThreadLocal<>();
    
    public StateManager(SharedPreferences prefs) {
        this.prefs = prefs;
    }
    
    /**
     * Begin a new state transaction
     */
    public void beginTransaction() {
        lock.lock();
        try {
            if (currentTransaction.get() != null) {
                Log.w(TAG, "Transaction already in progress, committing previous");
                commitTransaction();
            }
            currentTransaction.set(new Transaction());
            Log.v(TAG, "Started new transaction");
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Set a boolean value in the current transaction
     */
    public void putBoolean(String key, boolean value) {
        lock.lock();
        try {
            Transaction txn = currentTransaction.get();
            if (txn == null) {
                // No transaction, apply immediately
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(key, value);
                editor.apply();
                return;
            }
            
            // Store original value if not already tracked
            if (!txn.originalValues.containsKey(key)) {
                txn.originalValues.put(key, prefs.getBoolean(key, false));
            }
            txn.newValues.put(key, value);
            
            // Apply immediately but track for rollback
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(key, value);
            editor.apply();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Set an integer value in the current transaction
     */
    public void putInt(String key, int value) {
        lock.lock();
        try {
            Transaction txn = currentTransaction.get();
            if (txn == null) {
                // No transaction, apply immediately
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(key, value);
                editor.apply();
                return;
            }
            
            // Store original value if not already tracked
            if (!txn.originalValues.containsKey(key)) {
                txn.originalValues.put(key, prefs.getInt(key, 0));
            }
            txn.newValues.put(key, value);
            
            // Apply immediately but track for rollback
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(key, value);
            editor.apply();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Set a string value in the current transaction
     */
    public void putString(String key, String value) {
        lock.lock();
        try {
            Transaction txn = currentTransaction.get();
            if (txn == null) {
                // No transaction, apply immediately
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(key, value);
                editor.apply();
                return;
            }
            
            // Store original value if not already tracked
            if (!txn.originalValues.containsKey(key)) {
                txn.originalValues.put(key, prefs.getString(key, null));
            }
            txn.newValues.put(key, value);
            
            // Apply immediately but track for rollback
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(key, value);
            editor.apply();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Commit the current transaction
     */
    public void commitTransaction() {
        lock.lock();
        try {
            Transaction txn = currentTransaction.get();
            if (txn == null) {
                Log.w(TAG, "No transaction to commit");
                return;
            }
            
            txn.committed = true;
            currentTransaction.remove();
            Log.v(TAG, "Transaction committed with " + txn.newValues.size() + " changes");
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Rollback the current transaction
     */
    public void rollbackTransaction() {
        lock.lock();
        try {
            Transaction txn = currentTransaction.get();
            if (txn == null) {
                Log.w(TAG, "No transaction to rollback");
                return;
            }
            
            if (txn.committed) {
                Log.w(TAG, "Transaction already committed, cannot rollback");
                return;
            }
            
            // Restore original values
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, Object> entry : txn.originalValues.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value == null) {
                    editor.remove(key);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof String) {
                    editor.putString(key, (String) value);
                }
            }
            editor.apply();
            
            currentTransaction.remove();
            Log.d(TAG, "Transaction rolled back, restored " + txn.originalValues.size() + " values");
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Execute a runnable within a transaction with automatic rollback on error
     */
    public boolean executeInTransaction(Runnable task) {
        beginTransaction();
        try {
            task.run();
            commitTransaction();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error in transaction, rolling back", e);
            rollbackTransaction();
            return false;
        }
    }
    
    /**
     * Clear any pending transaction
     */
    public void clearTransaction() {
        lock.lock();
        try {
            currentTransaction.remove();
        } finally {
            lock.unlock();
        }
    }
}