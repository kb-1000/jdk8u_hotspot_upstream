/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

// A Symbol is a canonicalized string.
// All Symbols reside in global symbolTable.

public class Symbol extends Oop {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type  = db.lookupType("symbolOopDesc");
    length     = new CIntField(type.getCIntegerField("_length"), 0);
    baseOffset = type.getField("_body").getOffset();
  }

  // Format:
  //   [header]
  //   [klass ]
  //   [length] byte size of uft8 string
  //   ..body..

  Symbol(OopHandle handle, ObjectHeap heap) {
    super(handle, heap);
  }

  public boolean isSymbol()            { return true; }

  private static long baseOffset; // tells where the array part starts

  // Fields
  private static CIntField length;

  // Accessors for declared fields
  public long   getLength() { return          length.getValue(this); }

  public byte getByteAt(long index) {
    return getHandle().getJByteAt(baseOffset + index);
  }

  public boolean equals(byte[] modUTF8Chars) {
    int l = (int) getLength();
    if (l != modUTF8Chars.length) return false;
    while (l-- > 0) {
      if (modUTF8Chars[l] != getByteAt(l)) return false;
    }
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(l == -1, "we should be at the beginning");
    }
    return true;
  }

  public byte[] asByteArray() {
    int length = (int) getLength();
    byte [] result = new byte [length];
    for (int index = 0; index < length; index++) {
      result[index] = getByteAt(index);
    }
    return result;
  }

  public String asString() {
    // Decode the byte array and return the string.
    try {
      return readModifiedUTF8(asByteArray());
    } catch(IOException e) {
      return null;
    }
  }

  public boolean startsWith(String str) {
    return asString().startsWith(str);
  }

  public void printValueOn(PrintStream tty) {
    tty.print("#" + asString());
  }

  public long getObjectSize() {
    return alignObjectSize(baseOffset + getLength());
  }

  void iterateFields(OopVisitor visitor, boolean doVMFields) {
    super.iterateFields(visitor, doVMFields);
    if (doVMFields) {
      visitor.doCInt(length, true);
      int length = (int) getLength();
      for (int index = 0; index < length; index++) {
        visitor.doByte(new ByteField(new IndexableFieldIdentifier(index), baseOffset + index, false), true);
      }
    }
  }

  /** Note: this comparison is used for vtable sorting only; it
      doesn't matter what order it defines, as long as it is a total,
      time-invariant order Since symbolOops are in permSpace, their
      relative order in memory never changes, so use address
      comparison for speed. */
  public int fastCompare(Symbol other) {
    return (int) getHandle().minus(other.getHandle());
  }

  private static String readModifiedUTF8(byte[] buf) throws IOException {
    final int len = buf.length;
    byte[] tmp = new byte[len + 2];
    // write modified UTF-8 length as short in big endian
    tmp[0] = (byte) ((len >>> 8) & 0xFF);
    tmp[1] = (byte) ((len >>> 0) & 0xFF);
    // copy the data
    System.arraycopy(buf, 0, tmp, 2, len);
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(tmp));
    return dis.readUTF();
  }
}
