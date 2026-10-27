/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

// a channel value above this is white to the eye; a rendered page carries
// darker pixels wherever it has content
const WHITE_THRESHOLD = 245;

/**
 * Tells whether a loaded image is a blank page: the server answers a white
 * thumbnail for a document it could not render.
 *
 * @param {HTMLImageElement} image the loaded image
 * @returns {boolean} true when no pixel is darker than white, false when it
 *          has content or cannot be read
 */
export function isBlankImage(image) {
  try {
    const canvas = document.createElement('canvas');
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    if (!canvas.width || !canvas.height) {
      return true;
    }
    const context = canvas.getContext('2d');
    context.drawImage(image, 0, 0);
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    for (let i = 0; i < pixels.length; i += 4) {
      if (pixels[i] < WHITE_THRESHOLD || pixels[i + 1] < WHITE_THRESHOLD || pixels[i + 2] < WHITE_THRESHOLD) {
        return false;
      }
    }
    return true;
  } catch (e) {
    return false;
  }
}
