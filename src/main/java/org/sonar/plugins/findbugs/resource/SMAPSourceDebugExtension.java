/*
 * SonarQube Findbugs Plugin
 * Copyright (C) 2012 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.findbugs.resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SMAPSourceDebugExtension.java - Parse source debug extensions and
 * enhance stack traces.
 *
 * Copyright (c) 2012 Michael Schierl
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * - Neither name of the copyright holders nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND THE CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR THE CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * Utility class to parse Source Debug Extensions and enhance stack traces.
 *
 * Note that only the first stratum is parsed and used.
 *
 * @author Michael Schierl
 */
public class SMAPSourceDebugExtension {


    private final Map<Integer, FileInfo> fileinfo = new HashMap<Integer, FileInfo>();
    private final Map<Integer, List<Integer>> reverseLineMapping = new HashMap<Integer, List<Integer>>();

    private static final Pattern LINE_INFO_PATTERN = Pattern.compile("([0-9]+)(?:#([0-9]+))?(?:,([0-9]+))?:([0-9]+)(?:,([0-9]+))?");

    public SMAPSourceDebugExtension(String value) {
        String[] lines = value.split("\n");
        if (!lines[0].equals("SMAP") || !lines[3].startsWith("*S ") || !lines[4].equals("*F"))
            throw new IllegalArgumentException(value);
//        generatedFileName = lines[1];
//        firstStratum = lines[3].substring(3);
        int idx = 5;
        while (!lines[idx].startsWith("*")) {
            String infoLine = lines[idx++], path = null;
            if (infoLine.startsWith("+ ")) {
                path = lines[idx++];
                infoLine = infoLine.substring(2);
            }
            int pos = infoLine.indexOf(" ");
            int fileNum = Integer.parseInt(infoLine.substring(0, pos));
            String name = infoLine.substring(pos + 1);
            fileinfo.put(fileNum, new FileInfo(name, path == null ? name : path));
        }
        if (lines[idx].equals("*L")) {
            idx++;
            int lastLFI = 0;
            while (!lines[idx].startsWith("*")) {
                Matcher m = LINE_INFO_PATTERN.matcher(lines[idx++]);
                if (!m.matches())
                    throw new IllegalArgumentException(lines[idx - 1]);
                int inputStartLine = Integer.parseInt(m.group(1));
                int lineFileID = m.group(2) == null ? lastLFI : Integer.parseInt(m.group(2));
                int repeatCount = m.group(3) == null ? 1 : Integer.parseInt(m.group(3));
                int outputStartLine = Integer.parseInt(m.group(4));
                int outputLineIncrement = m.group(5) == null ? 1 : Integer.parseInt(m.group(5));
                for (int i = 0; i < repeatCount; i++) {
                    List<Integer> inputMapping = Arrays.asList( lineFileID, inputStartLine + i);
                    int baseOL = outputStartLine + i * outputLineIncrement;
                    for (int ol = baseOL; ol < baseOL + outputLineIncrement; ol++) {
                        if (!reverseLineMapping.containsKey(ol))
                            reverseLineMapping.put(ol, inputMapping);
                    }
                }
                lastLFI = lineFileID;
            }
        }
    }

    public List<Integer> getJspLineNumber(Integer lineNo) {

        return reverseLineMapping.get(lineNo);
    }

    private static class FileInfo {
        public final String name, path;

        public FileInfo(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }
}