/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

export async function hasPassword() {
  const resp = await fetch('/social/rest/apiKey', {
    method: 'GET',
    credentials: 'include',
  });
  if (resp?.ok) {
    return (await resp.text()) === 'true';
  } else {
    const msg = resp ? await resp.text() : 'Unkown error';
    throw new Error(`Server Error: ${msg}`);
  }
}

export async function getPassword(optMethod, otpCode, renew) {
  const formData = new FormData();
  formData.append('method', optMethod);
  formData.append('code', otpCode);
  formData.append('renew', !!renew);
  const resp = await fetch('/social/rest/apiKey', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  });
  if (resp?.ok) {
    return await resp.text();
  } else {
    const msg = resp ? await resp.text() : 'Unkown error';
    throw new Error(`Server Error: ${msg}`);
  }
}
