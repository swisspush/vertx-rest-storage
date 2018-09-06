package org.swisspush.reststorage;

import org.junit.Assert;
import org.junit.Test;

public class MimeTypeResolverTest {

    private MimeTypeResolver mimeTypeResolver=new MimeTypeResolver("gugus/gaga");

    @Test
    public void testDefault() {
        String s = mimeTypeResolver.resolveMimeType("/aaa/bbb/ccc");// no file extension --> default
        Assert.assertEquals("Default-MimeType", "gugus/gaga", s);
    }

    @Test
    public void testUnknownFileExtension() {
        String s = mimeTypeResolver.resolveMimeType("/aaa/bbb/ccc.i_am_an_unknown_file_extension");// unknown extension --> text/plain
        Assert.assertEquals("Default-MimeType", "text/plain", s);
    }

    @Test
    public void testJson() {
        String s = mimeTypeResolver.resolveMimeType("/aaa/bbb/ccc.json");
        Assert.assertEquals("Default-MimeType", "application/json", s);
    }

    @Test
    public void testTarGz() {
        String s = mimeTypeResolver.resolveMimeType("/aaa/bbb/ccc.tar.gz");
        Assert.assertEquals("Default-MimeType", "application/gzip", s);
    }
}
