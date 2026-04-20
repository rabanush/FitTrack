/* ============================================================
   FitTrack – Dark-mode app logic
   ============================================================ */

(function () {
  "use strict";

  if (!Array.isArray(window.exercises)) return;

  /* ---------- State ---------- */
  const state = {
    filter: "all",
    search: "",
    difficulty: "all",
  };
  const indexedExercises = window.exercises.map((ex) => ({
    ...ex,
    searchBlob: [ex.name, ...ex.muscles, ex.description].join(" ").toLowerCase(),
    descriptionPreview:
      ex.description.length > 110 ? `${ex.description.slice(0, 110)}…` : ex.description,
  }));

  /* ---------- DOM refs ---------- */
  const grid = document.getElementById("exerciseGrid");
  const emptyState = document.getElementById("emptyState");
  const searchInput = document.getElementById("searchInput");
  const difficultySelect = document.getElementById("difficultySelect");
  const filterBtns = document.querySelectorAll(".filter-btn");
  const modal = document.getElementById("videoModal");
  const modalTitle = document.getElementById("modalTitle");
  const modalDescription = document.getElementById("modalDescription");
  const modalMuscles = document.getElementById("modalMuscles");
  const videoFrame = document.getElementById("videoFrame");
  const closeModalBtn = document.getElementById("closeModal");
  const exerciseCount = document.getElementById("exerciseCount");
  let lastFocusedElement = null;

  /* ---------- Render ---------- */
  function render() {
    const q = state.search.trim().toLowerCase();
    const filtered = indexedExercises.filter((ex) => {
      const matchCategory =
        state.filter === "all" || ex.category === state.filter;
      const matchDifficulty =
        state.difficulty === "all" || ex.difficulty === state.difficulty;
      const matchSearch = !q || ex.searchBlob.includes(q);
      return matchCategory && matchDifficulty && matchSearch;
    });

    exerciseCount.textContent = `${filtered.length} exercise${filtered.length !== 1 ? "s" : ""}`;

    grid.innerHTML = "";
    if (filtered.length === 0) {
      emptyState.hidden = false;
    } else {
      emptyState.hidden = true;
      const cardsFragment = document.createDocumentFragment();
      filtered.forEach((ex) => cardsFragment.appendChild(createCard(ex)));
      grid.appendChild(cardsFragment);
    }
  }

  /* ---------- Card builder ---------- */
  function createCard(ex) {
    const card = document.createElement("article");
    card.className = "exercise-card";
    card.setAttribute("tabindex", "0");
    card.setAttribute("aria-label", `Open details for ${ex.name}`);

    const diffLabel = {
      beginner: "Beginner",
      intermediate: "Intermediate",
      advanced: "Advanced",
    }[ex.difficulty];

    card.innerHTML = `
      <div class="card-header">
        <span class="category-badge category-${ex.category}">${capitalise(ex.category)}</span>
        <span class="difficulty-badge difficulty-${ex.difficulty}">${diffLabel}</span>
      </div>
      <h3 class="card-title">${ex.name}</h3>
      <p class="card-description">${ex.descriptionPreview}</p>
      <ul class="muscle-list">
        ${ex.muscles.map((m) => `<li>${m}</li>`).join("")}
      </ul>
      <div class="card-footer">
        <span class="stat"><span class="stat-label">Sets</span><span class="stat-value">${ex.sets}</span></span>
        <span class="stat"><span class="stat-label">Reps</span><span class="stat-value">${ex.reps}</span></span>
        <button class="watch-btn" data-id="${ex.id}" aria-label="Watch tutorial for ${ex.name}">
          <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14" aria-hidden="true">
            <path d="M8 5v14l11-7z"/>
          </svg>
          Watch
        </button>
      </div>`;

    /* Open modal on click or Enter/Space */
    card.addEventListener("click", (e) => {
      if (!e.target.closest(".watch-btn")) openModal(ex);
    });
    card.addEventListener("keydown", (e) => {
      if ((e.key === "Enter" || e.key === " ") && e.target === card) {
        if (e.key === " ") e.preventDefault();
        openModal(ex);
      }
    });
    card.querySelector(".watch-btn").addEventListener("click", (e) => {
      e.stopPropagation();
      openModal(ex);
    });

    return card;
  }

  /* ---------- Modal ---------- */
  function openModal(ex) {
    lastFocusedElement = document.activeElement;
    modalTitle.textContent = ex.name;
    modalDescription.textContent = ex.description;
    modalMuscles.innerHTML = ex.muscles
      .map((m) => `<span class="modal-muscle-tag">${m}</span>`)
      .join("");
    videoFrame.src = `https://www.youtube-nocookie.com/embed/${ex.videoId}?rel=0&modestbranding=1`;
    modal.hidden = false;
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("modal-open");
    closeModalBtn.focus();
  }

  function closeModal() {
    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    videoFrame.src = "";
    document.body.classList.remove("modal-open");
    if (lastFocusedElement instanceof HTMLElement) lastFocusedElement.focus();
    lastFocusedElement = null;
  }

  closeModalBtn.addEventListener("click", closeModal);
  modal.addEventListener("click", (e) => {
    if (e.target === modal) closeModal();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !modal.hidden) closeModal();
  });

  /* ---------- Filters ---------- */
  filterBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      filterBtns.forEach((b) => {
        b.classList.remove("active");
        b.setAttribute("aria-pressed", "false");
      });
      btn.classList.add("active");
      btn.setAttribute("aria-pressed", "true");
      state.filter = btn.dataset.category;
      render();
    });
  });

  searchInput.addEventListener("input", () => {
    state.search = searchInput.value;
    render();
  });

  difficultySelect.addEventListener("change", () => {
    state.difficulty = difficultySelect.value;
    render();
  });

  /* ---------- Helpers ---------- */
  function capitalise(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  /* ---------- Boot ---------- */
  render();
})();
