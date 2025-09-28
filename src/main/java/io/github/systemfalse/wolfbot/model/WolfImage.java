/*
 * Copyright (c) 2025 SystemFalse
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.systemfalse.wolfbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "wolf_images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WolfImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "file_name", nullable = false)
    String fileName;

    @Lob
    @Column(name = "file_data", nullable = false)
    byte[] fileData;

    @Column(name = "file_size")
    long fileSize;

    @Column(name = "mime_type", length = 100)
    String mimeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    User uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    ImageStatus status = ImageStatus.PENDING;

    @Builder.Default
    @Column(name = "uploaded_at", nullable = false)
    LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "moderated_at")
    LocalDateTime moderatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    Moderator moderatedBy;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Builder.Default
    @Column(name = "send_count")
    Integer sendCount = 0;

    @Column(name = "last_sent")
    LocalDateTime lastSent;

    public void markAsSent() {
        sendCount++;
        lastSent = LocalDateTime.now();
    }

    public void moderate(ImageStatus status, Moderator moderator, String reason) {
        this.status = status;
        this.moderatedBy = moderator;
        this.moderatedAt = LocalDateTime.now();
        this.moderationReason = reason;
    }
}
