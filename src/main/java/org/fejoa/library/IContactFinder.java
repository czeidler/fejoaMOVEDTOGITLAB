package org.fejoa.library;

public interface IContactFinder {
    public Contact find(String keyId);

    Contact findByAddress(String address);
}
