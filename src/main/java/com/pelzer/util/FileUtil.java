package com.pelzer.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {
  public static void copy(final File from, final File to, final boolean overwrite) throws IOException {
    if (to.exists() && !overwrite)
      throw new IOException("Target file already exists and overwrite is false.");
    final BufferedInputStream in = new BufferedInputStream(new FileInputStream(from));
    final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(to));
    while (true) {
      final int bit = in.read();
      if (bit < 0) {
        break;
      }
      out.write(bit);
    }
    in.close();
    out.close();
  }
}
