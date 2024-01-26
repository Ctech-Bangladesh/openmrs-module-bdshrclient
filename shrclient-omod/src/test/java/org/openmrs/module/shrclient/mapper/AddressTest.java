package org.openmrs.module.shrclient.mapper;

import org.junit.Test;
import org.openmrs.module.shrclient.model.Address;

import static org.junit.Assert.assertEquals;

public class AddressTest {
    @Test
    public void shouldGetCorrectAddressCode() {
        assertEquals("10", Address.getAddressCodeForLevel("1004092001", 1));
        assertEquals("04", Address.getAddressCodeForLevel("1004092001", 2));
        assertEquals("09", Address.getAddressCodeForLevel("1004092001", 3));
        assertEquals("20", Address.getAddressCodeForLevel("1004092001", 4));
        assertEquals("01", Address.getAddressCodeForLevel("1004092001", 5));
    }

    @Test
    public void shouldGetConcatenatedAddressCodeForUpazillaLevel() throws Exception {
        Address address = new Address("xyz", "30", "26", "06", null, null, null, null);
        assertEquals("302606", address.getAddressCode());
    }

    @Test
    public void shouldGetConcatenatedAddressCodeForRuralLevel() throws Exception {
        Address address = new Address("xyz", "30", "26", "06", "14", "85", "23", "03");
        assertEquals("302606148523", address.getAddressCode());
    }
}
