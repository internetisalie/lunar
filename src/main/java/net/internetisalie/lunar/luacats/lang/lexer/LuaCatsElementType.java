/*
 * Copyright 2011 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package net.internetisalie.lunar.luacats.lang.lexer;

import com.intellij.psi.tree.IElementType;
import net.internetisalie.lunar.lang.LuaLanguage;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */
public class LuaCatsElementType extends IElementType {

  private String debugName = null;

  public LuaCatsElementType(@NonNls String debugName) {
    super(debugName, LuaLanguage.INSTANCE);
    this.debugName = debugName;
  }

  public String toString() {
    return debugName;
  }
}