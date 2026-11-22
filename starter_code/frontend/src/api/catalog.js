const BASE_URL = '/api/courses';

export async function fetchCourses({ level, keyword } = {}) {
  const params = new URLSearchParams();
  if (level) {
    params.set('level', level);
  }
  if (keyword) {
    params.set('q', keyword);
  }

  const query = params.toString();
  const response = await fetch(query ? `${BASE_URL}?${query}` : BASE_URL);

  if (!response.ok) {
    throw new Error(`Catalogue request failed with status ${response.status}`);
  }
  return response.json();
}

export async function fetchCourseByCode(code) {
  const response = await fetch(`${BASE_URL}/${encodeURIComponent(code)}`);
  if (!response.ok) {
    throw new Error(`Course ${code} could not be loaded`);
  }
  return response.json();
}
